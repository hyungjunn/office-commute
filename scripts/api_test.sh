#!/bin/bash

# OS 호환 날짜 계산용

# gdate가 있으면 사용하고, 없으면 BSD date(macOS)
if command -v gdate &>/dev/null; then
  DATE_CMD="gdate"
  get_date() {
    offset=$1
    $DATE_CMD -d "$offset" +%F
  }
else
  DATE_CMD="date"
  get_date() {
    offset=$1
    case "$offset" in
      "+*" ) days=${offset:1}; $DATE_CMD -v+${days%d*}d +%F ;;
      "-*" ) days=${offset:1}; $DATE_CMD -v-${days%d*}d +%F ;;
      *    ) $DATE_CMD +%F ;;
    esac
  }
fi

# 날짜 계산
today=$($DATE_CMD +%F)
year_month=$($DATE_CMD +%Y-%m)

# 미래 연차 날짜 (today 기준 +5일, +6일, +7일)
future_date1=$(get_date "+5 days")
future_date2=$(get_date "+6 days")
future_date3=$(get_date "+7 days")

# 과거 연차 날짜 (today 기준 -30일, -29일)
past_date1=$(get_date "-30 days")
past_date2=$(get_date "-29 days")

# 서버 URL
base_url="http://localhost:8080"

# API 함수 정의

create_team() {
  team_name=$1
  http -v POST "$base_url/team" teamName="$team_name"
}

create_employee() {
  name=$1
  role=$2
  birthday=$3
  work_start_date=$4
  http -v POST "$base_url/employee" name="$name" role="$role" birthday="$birthday" workStartDate="$work_start_date"
}

assign_employee_to_team() {
  employee_id=$1
  team_name=$2
  http -v PUT "$base_url/employee" employeeId="$employee_id" teamName="$team_name"
}

register_work_start_time() {
  employee_id=$1
  http -v POST "$base_url/commute" employeeId="$employee_id"
}

register_work_end_time() {
  employee_id=$1
  http -v PUT "$base_url/commute" employeeId="$employee_id"
}

request_annual_leave() {
  employee_id=$1
  wanted_dates=$2
  http -v POST "$base_url/annual-leave" employeeId="$employee_id" wantedDates:="$wanted_dates"
}

get_remaining_annual_leave() {
  employee_id=$1
  http -v GET "$base_url/annual-leave?employeeId=$employee_id"
}

get_work_duration_per_date() {
  employee_id=$1
  year_month=$2
  http -v GET "$base_url/commute?employeeId=$employee_id&yearMonth=$year_month"
}

# API 테스트 실행

create_team "백엔드"
create_employee "임형준" "MANAGER" "1995-05-15" "$today"
create_employee "고슬링" "MEMBER" "1950-05-15" "$today"
create_employee "존카맥" "MEMBER" "1960-05-15" "$today"

assign_employee_to_team 1 "백엔드"

register_work_start_time 1
register_work_end_time 1

request_annual_leave 1 "[\"$future_date1\", \"$future_date2\", \"$future_date3\"]"
request_annual_leave 1 "[\"$past_date1\", \"$past_date2\"]"

get_remaining_annual_leave 1
get_work_duration_per_date 1 "$year_month"
