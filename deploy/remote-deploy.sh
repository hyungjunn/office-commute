#!/usr/bin/env bash

set -Eeuo pipefail

if [[ "$#" -ne 7 ]]; then
  printf 'usage: %s <release-dir> <sha> <deploy-root> <image-uri> <registry> <region> <domain>\n' "$0" >&2
  exit 64
fi

release_dir="$1"
release_sha="$2"
deploy_root="$3"
image_uri="$4"
registry="$5"
aws_region="$6"
deploy_domain="$7"

log() {
  printf '[deploy] %s\n' "$*"
}

die() {
  printf '[deploy] ERROR: %s\n' "$*" >&2
  exit 1
}

write_deploy_file() {
  local source_file="$1"
  local destination_file="$2"

  # nginx.conf is a file bind mount. Updating an existing inode keeps the mounted
  # file visible inside the already-running nginx container.
  if [[ -f "$destination_file" ]]; then
    cp -- "$source_file" "$destination_file"
    chmod 0644 "$destination_file"
  else
    install -m 0644 "$source_file" "$destination_file"
  fi
}

if [[ "$EUID" -ne 0 ]]; then
  die "the SSM deployment must run as root"
fi

if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  die "invalid release SHA"
fi

if [[ "$deploy_root" != "/home/ubuntu/office-commute" ]]; then
  die "unexpected deployment root: $deploy_root"
fi

if [[ "$release_dir" != "$deploy_root/releases/$release_sha" ]]; then
  die "unexpected release directory: $release_dir"
fi

for command_name in aws curl docker flock grep rsync; do
  command -v "$command_name" >/dev/null 2>&1 || die "required command is not installed: $command_name"
done

env_file="$deploy_root/.env"
prod_compose="$deploy_root/docker-compose.prod.yml"
mysql_compose="$deploy_root/docker-compose.mysql.yml"
nginx_config="$deploy_root/nginx.conf"
dist_dir="$deploy_root/dist"

[[ -f "$env_file" ]] || die "missing server environment file: $env_file"
[[ -f "$release_dir/dist/index.html" ]] || die "release does not contain frontend/dist"

umask 077
install -d -m 0700 "$deploy_root/backups"
exec 9>"$deploy_root/.deploy.lock"
flock -n 9 || die "another deployment is already running"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$deploy_root/backups/$timestamp-$release_sha"
install -d -m 0700 "$backup_dir"

log "backing up the current deployment to $backup_dir"
cp -p -- "$env_file" "$backup_dir/.env"
for deployed_file in "$prod_compose" "$mysql_compose" "$nginx_config"; do
  if [[ -f "$deployed_file" ]]; then
    cp -p -- "$deployed_file" "$backup_dir/"
  fi
done
if [[ -d "$dist_dir" ]]; then
  cp -a -- "$dist_dir" "$backup_dir/dist"
fi

log "installing deployment configuration"
write_deploy_file "$release_dir/deploy/docker-compose.prod.yml" "$prod_compose"
write_deploy_file "$release_dir/deploy/docker-compose.mysql.yml" "$mysql_compose"
write_deploy_file "$release_dir/deploy/nginx.conf" "$nginx_config"

env_next="$deploy_root/.env.next.$release_sha"
: > "$env_next"
found_app_image=false
while IFS= read -r env_line || [[ -n "$env_line" ]]; do
  if [[ "$env_line" == APP_IMAGE=* ]]; then
    printf "APP_IMAGE='%s'\n" "$image_uri" >> "$env_next"
    found_app_image=true
  else
    printf '%s\n' "$env_line" >> "$env_next"
  fi
done < "$env_file"
if [[ "$found_app_image" == false ]]; then
  printf "APP_IMAGE='%s'\n" "$image_uri" >> "$env_next"
fi
chmod --reference="$env_file" "$env_next"
chown --reference="$env_file" "$env_next"
mv -- "$env_next" "$env_file"

compose=(
  docker compose
  --env-file "$env_file"
  -f "$prod_compose"
  -f "$mysql_compose"
)

restore_frontend() {
  if [[ -d "$backup_dir/dist" ]]; then
    rsync -a --delete "$backup_dir/dist/" "$dist_dir/"
  fi
  if [[ -f "$backup_dir/nginx.conf" ]]; then
    write_deploy_file "$backup_dir/nginx.conf" "$nginx_config"
  fi
  "${compose[@]}" exec -T nginx nginx -t
  "${compose[@]}" exec -T nginx nginx -s reload
}

"${compose[@]}" config --quiet

logged_in=false
logout_ecr() {
  if [[ "$logged_in" == true ]]; then
    docker logout "$registry" >/dev/null 2>&1 || true
  fi
}
trap logout_ecr EXIT

log "pulling backend image $image_uri"
aws ecr get-login-password --region "$aws_region" \
  | docker login --username AWS --password-stdin "$registry" >/dev/null
logged_in=true
"${compose[@]}" pull app

log "starting backend before publishing the frontend"
"${compose[@]}" up -d app

backend_status=""
for _ in $(seq 1 40); do
  backend_status="$(curl \
    --silent \
    --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --connect-timeout 3 \
    --max-time 8 \
    --resolve "$deploy_domain:443:127.0.0.1" \
    "https://$deploy_domain/api/auth/me" || true)"
  if [[ "$backend_status" == "401" ]]; then
    break
  fi
  sleep 3
done

if [[ "$backend_status" != "401" ]]; then
  die "backend smoke test failed with HTTP ${backend_status:-unreachable}; automatic backend rollback is disabled because Flyway migrations may not be reversible (backup: $backup_dir)"
fi

log "backend smoke test passed"
install -d -m 0755 "$dist_dir"
rsync -a --delete --delay-updates "$release_dir/dist/" "$dist_dir/"

if ! "${compose[@]}" exec -T nginx nginx -t; then
  restore_frontend
  die "nginx configuration test failed; frontend files were restored"
fi
"${compose[@]}" exec -T nginx nginx -s reload

expected_asset="$(grep -oE '/assets/[^"[:space:]]+\.js' "$release_dir/dist/index.html" | head -n 1 || true)"
homepage="$(mktemp)"
frontend_status="$(curl \
  --silent \
  --show-error \
  --output "$homepage" \
  --write-out '%{http_code}' \
  --connect-timeout 3 \
  --max-time 10 \
  --resolve "$deploy_domain:443:127.0.0.1" \
  "https://$deploy_domain/" || true)"

frontend_ok=true
if [[ "$frontend_status" != "200" ]]; then
  frontend_ok=false
elif [[ -z "$expected_asset" ]] || ! grep -Fq "$expected_asset" "$homepage"; then
  frontend_ok=false
fi
rm -f -- "$homepage"

if [[ "$frontend_ok" != true ]]; then
  log "frontend verification failed; restoring the previous dist and nginx config"
  restore_frontend
  die "frontend smoke test failed with HTTP ${frontend_status:-unreachable}"
fi

printf '%s\n' "$release_sha" > "$deploy_root/.deployed-revision"
chmod 0644 "$deploy_root/.deployed-revision"
log "deployment completed: $release_sha"
