#!/bin/bash

# 서버의 기본 URL
base_url="http://localhost:8080"

# 팀 생성 함수
create_team() {
  team_name=$1
  http -v POST "$base_url/team" teamName="$team_name"
}

# 사원 생성 함수
create_employee() {
  name=$1
  role=$2
  birthday=$3
  work_start_date=$4
  http -v POST "$base_url/employee" name="$name" role="$role" birthday="$birthday" workStartDate="$work_start_date"
}

# 사원을 팀에 배정하는 함수
assign_employee_to_team() {
  employee_id=$1
  team_name=$2
  http -v PUT "$base_url/employee" employeeId="$employee_id" teamName="$team_name"
}

# 연차 신청 함수
request_annual_leave() {
  employee_id=$1
  wanted_dates=$2
  http -v POST "$base_url/annual-leave" employeeId="$employee_id" wantedDates:="$wanted_dates"
}

# 예시 사용법
create_team "백엔드"
create_employee "임형준" "MANAGER" "1995-05-15" "2024-01-02"
assign_employee_to_team 1 "백엔드"
request_annual_leave 1 '["2024-12-30", "2024-12-31"]'
