#!/usr/bin/env bash

set -Eeuo pipefail

if [[ "$#" -ne 8 ]]; then
  printf 'usage: %s <bucket> <sha> <checksum> <deploy-root> <image-uri> <registry> <region> <domain>\n' "$0" >&2
  exit 64
fi

bucket="$1"
release_sha="$2"
expected_checksum="$3"
deploy_root="$4"
image_uri="$5"
registry="$6"
aws_region="$7"
deploy_domain="$8"

if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'invalid release SHA\n' >&2
  exit 64
fi

if [[ ! "$expected_checksum" =~ ^[0-9a-f]{64}$ ]]; then
  printf 'invalid release checksum\n' >&2
  exit 64
fi

if [[ "$deploy_root" != "/home/ubuntu/office-commute" ]]; then
  printf 'unexpected deployment root: %s\n' "$deploy_root" >&2
  exit 64
fi

for command_name in aws sha256sum tar; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'required command is not installed: %s\n' "$command_name" >&2
    exit 69
  fi
done

umask 077
incoming_dir="$deploy_root/incoming"
releases_dir="$deploy_root/releases"
artifact_path="$incoming_dir/release-$release_sha.tar.gz"
release_dir="$releases_dir/$release_sha"
staging_dir=""

cleanup() {
  rm -f -- "$artifact_path"
  if [[ -n "$staging_dir" && -d "$staging_dir" ]]; then
    rm -rf -- "$staging_dir"
  fi
}
trap cleanup EXIT

install -d -m 0755 "$incoming_dir" "$releases_dir"

printf '[bootstrap] downloading release %s\n' "$release_sha"
aws s3 cp \
  "s3://$bucket/releases/$release_sha/release.tar.gz" \
  "$artifact_path" \
  --region "$aws_region" \
  --only-show-errors

printf '%s  %s\n' "$expected_checksum" "$artifact_path" | sha256sum --check --status

staging_dir="$(mktemp -d "$releases_dir/.${release_sha}.XXXXXX")"
if tar -tzf "$artifact_path" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
  printf 'release contains an unsafe archive path\n' >&2
  exit 65
fi
tar --no-same-owner -xzf "$artifact_path" -C "$staging_dir"

for required_file in \
  "$staging_dir/REVISION" \
  "$staging_dir/dist/index.html" \
  "$staging_dir/deploy/docker-compose.prod.yml" \
  "$staging_dir/deploy/docker-compose.mysql.yml" \
  "$staging_dir/deploy/nginx.conf" \
  "$staging_dir/deploy/remote-deploy.sh"; do
  if [[ ! -f "$required_file" ]]; then
    printf 'release is missing required file: %s\n' "$required_file" >&2
    exit 65
  fi
done

if [[ "$(tr -d '\r\n' < "$staging_dir/REVISION")" != "$release_sha" ]]; then
  printf 'release revision does not match requested SHA\n' >&2
  exit 65
fi

if [[ -e "$release_dir" ]]; then
  rm -rf -- "$release_dir"
fi
mv -- "$staging_dir" "$release_dir"
staging_dir=""
chmod 0755 "$release_dir/deploy/remote-deploy.sh"

exec "$release_dir/deploy/remote-deploy.sh" \
  "$release_dir" \
  "$release_sha" \
  "$deploy_root" \
  "$image_uri" \
  "$registry" \
  "$aws_region" \
  "$deploy_domain"
