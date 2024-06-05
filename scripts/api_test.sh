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

# 사원 출근 함수
register_work_start_time() {
  employee_id=$1
  http -v POST "$base_url/commute" employeeId="$employee_id"
}

# 사원 퇴근 함수
register_work_end_time() {
  employee_id=$1
  http -v PUT "$base_url/commute" employeeId="$employee_id"
}

# 연차 신청 함수
request_annual_leave() {
  employee_id=$1
  wanted_dates=$2
  http -v POST "$base_url/annual-leave" employeeId="$employee_id" wantedDates:="$wanted_dates"
}

# 남은 연차 조회
get_remaining_annual_leave() {
  employee_id=$1
  http -v GET "$base_url/annual-leave?employeeId=$employee_id"
}

# 특정 월에 근무시간 조회
get_work_duration_per_date() {
  employee_id=$1
  year_month=$2
  http -v GET "$base_url/commute?employeeId=$employee_id&yearMonth=$year_month"
}

# 예시 사용법
create_team "백엔드"
create_employee "임형준" "MANAGER" "1995-05-15" "2024-01-02"
create_employee "고슬링" "MEMBER" "1950-05-15" "2024-01-02"
create_employee "존카맥" "MEMBER" "1960-05-15" "2024-01-02"

assign_employee_to_team 1 "백엔드"

register_work_start_time 1
regitser_work_end_time 1

request_annual_leave 1 '["2024-12-28", "2024-12-29", "2024-12-30"]'
request_annual_leave 1 '["2024-06-29", "2024-06-30"]'

get_remaining_annual_leave 1 

get_work_duration_per_date 1 "2024-06"

