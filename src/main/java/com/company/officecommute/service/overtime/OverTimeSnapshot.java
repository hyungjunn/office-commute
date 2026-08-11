package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.employee.Employee;

import java.util.List;

/**
 * 한 번의 읽기 트랜잭션에서 함께 뜬 리포트 입력. 두 조회가 같은 스냅샷에서 나왔다는 사실을
 * 타입으로 묶어 둔다 — 따로 부르면 그 사이의 출퇴근 기록 변경이 "직원은 있는데 근무 기록은
 * 다른 시점" 같은 비일관을 만든다.
 */
public record OverTimeSnapshot(
        List<Employee> employees,
        List<DailyWorkingMinutes> dailyWorkingMinutes
) {
}
