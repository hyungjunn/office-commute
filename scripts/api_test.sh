#!/bin/bash

# ▒▒▒ OS 호환 날짜 계산 (Python 사용) ▒▒▒
# Python을 사용하여 날짜를 계산하므로 macOS와 Linux에서 모두 동일하게 작동합니다.
get_date() {
    local offset="$1"
    # Python을 실행하여 날짜 계산 후 결과를 받아옵니다.
    python3 -c "from datetime import date, timedelta; print(date.today() + timedelta(days=${offset}))"
}

# ▒▒▒ 실패 추적 및 테스트 래퍼 ▒▒▒
errors=()
passed_count=0
failed_count=0

# 테스트 실행 및 결과 기록 함수
do_test_step() {
  description=$1
  shift
  echo "-----------------------------------------------------"
  echo "🚀 Executing: $description"
  echo "-----------------------------------------------------"
  if "$@"; then
    echo "✅ SUCCESS: $description"
    passed_count=$((passed_count + 1))
  else
    echo "❌ FAILED: $description"
    errors+=("$description")
    failed_count=$((failed_count + 1))
  fi
  echo
}

# ▒▒▒ 날짜 변수 설정 ▒▒▒
today=$(get_date "0")
year_month=$(date +%Y-%m) # 이 형식은 macOS와 Linux에서 동일하게 작동합니다.

# 미래 연차 날짜 (today 기준 +5일, +6일, +7일)
future_date1=$(get_date "+5")
future_date2=$(get_date "+6")
future_date3=$(get_date "+7")

echo "✅ Today's Date: $today"
echo "✅ Future leave dates: $future_date1, $future_date2, $future_date3"
echo "✅ Past leave dates: $past_date1, $past_date2"
echo "✅ Current Year-Month: $year_month"
echo

# ▒▒▒ 서버 및 세션 정보 ▒▒▒
base_url="http://localhost:8080"
# 세션을 유지하기 위한 httpie의 세션 파일 이름
SESSION="api-test-session"

# === API 함수 정의 ===
# API 호출 시 verbose(-v) 옵션은 디버깅 시 유용하지만, 테스트 결과만 깔끔하게 보려면 제거하는 것이 좋습니다.
# 여기서는 그대로 유지합니다.
login_admin() {
  http -v --session="$SESSION" POST "$base_url/login" \
      employeeCode="ADMIN001" \
      password="admin123!"
}

create_team() {
  team_name=$1
  http -v --session="$SESSION" POST "$base_url/team" teamName="$team_name"
}

create_employee() {
  name=$1
  role=$2
  birthday=$3
  work_start_date=$4
  employee_code=$5
  password=$6
  http -v --session="$SESSION" POST "$base_url/employee" \
    name="$name" \
    role="$role" \
    birthday="$birthday" \
    workStartDate="$work_start_date" \
    employeeCode="$employee_code" \
    password="$password"
}

assign_employee_to_team() {
  employee_id=$1
  team_name=$2
  http -v --session="$SESSION" PUT "$base_url/employee" employeeId:="$employee_id" teamName="$team_name"
}

login_employee() {
  employee_code=$1
  password=$2
  http -v --session="$SESSION" POST "$base_url/login" \
      employeeCode="$employee_code" \
      password="$password"
}

register_work_start_time() {
  http -v --session="$SESSION" POST "$base_url/commute"
}

register_work_end_time() {
  http -v --session="$SESSION" PUT "$base_url/commute"
}

request_annual_leave() {
  # JSON 배열을 직접 인라인으로 전달합니다.
  wanted_dates=$1
  http -v --session="$SESSION" POST "$base_url/annual-leave" wantedDates:="$wanted_dates"
}

get_remaining_annual_leave() {
  http -v --session="$SESSION" GET "$base_url/annual-leave"
}

get_work_duration_per_date() {
  year_month=$1
  http -v --session="$SESSION" GET "$base_url/commute?yearMonth=$year_month"
}

# === API 테스트 실행 ===
do_test_step "관리자 로그인" login_admin

do_test_step "팀 생성 (백엔드)" create_team "백엔드"

do_test_step "임형준 사원 생성" create_employee "임형준" "MANAGER" "1995-05-15" "$today" "EMP001" "password123!"
do_test_step "고슬링 사원 생성" create_employee "고슬링" "MEMBER" "1950-05-15" "$today" "EMP002" "password123!"
do_test_step "존카맥 사원 생성" create_employee "존카맥" "MEMBER" "1960-05-15" "$today" "EMP003" "password123!"

# ID가 2번인 '고슬링' 사원을 '백엔드' 팀에 배정 (ID가 순차적으로 생성된다고 가정)
do_test_step "팀 배정 (고슬링 to 백엔드)" assign_employee_to_team 2 "백엔드"

do_test_step "사원 로그인 (임형준)" login_employee "EMP001" "password123!"

do_test_step "출근 등록" register_work_start_time
# 실제 테스트 시나리오를 위해 출퇴근 사이에 약간의 시간 간격을 둡니다.
sleep 2
do_test_step "퇴근 등록" register_work_end_time

# JSON 배열 형식으로 날짜를 전달합니다.
do_test_step "미래 연차 신청" request_annual_leave "[\"$future_date1\", \"$future_date2\", \"$future_date3\"]"

do_test_step "남은 연차 조회" get_remaining_annual_leave
do_test_step "월별 근무 시간 조회" get_work_duration_per_date "$year_month"


# === 테스트 결과 리포트 ===
echo
echo "========================================="
echo "🧪           TEST REPORT           🧪"
echo "========================================="

echo "✅ Passed: $passed_count"
echo "❌ Failed: $failed_count"
echo

if [ "$failed_count" -gt 0 ]; then
  echo "--- Failed Steps ---"
  for err in "${errors[@]}"; do
    echo "  - $err"
  done
  echo
  echo "========================================="
  echo "🚨            RESULT: FAILED            🚨"
  echo "========================================="
  exit 1
else
  echo "========================================="
  echo "🎉        RESULT: ALL TESTS PASSED       🎉"
  echo "========================================="
  exit 0
fi
