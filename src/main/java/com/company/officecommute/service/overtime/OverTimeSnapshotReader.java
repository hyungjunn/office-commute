package com.company.officecommute.service.overtime;

import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초과근무 계산이 필요로 하는 두 조회를 <b>하나의 읽기 트랜잭션</b>으로 묶는다.
 * <p>
 * 따로 부르면 두 쿼리 사이에 출퇴근 기록이나 직원 정보가 바뀌어 비일관 스냅샷이 나올 수 있다.
 * 급여 근거 자료에서 그 종류의 어긋남은 조용히 틀린 값이 된다.
 * <p>
 * 별도 빈으로 뽑은 이유: 공휴일 API 라이브 호출({@code HolidayApiClient})은 트랜잭션 <b>밖</b>에
 * 있어야 하는데, 같은 클래스 안에서 {@code @Transactional} 메서드를 자기호출하면 프록시를
 * 타지 않아 애초에 트랜잭션이 걸리지 않는다. 경계를 클래스 경계와 일치시킨다.
 */
@Component
public class OverTimeSnapshotReader {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;

    public OverTimeSnapshotReader(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public OverTimeSnapshot read(OverTimePeriod period) {
        return new OverTimeSnapshot(
                // 대상자 판정은 월 경계 기준 — 스필오버 주(전월 말)의 근무는 그 직원의 전월 리포트가 이미 집계했다
                employeeRepository.findAllWithTeamEmployedBetween(
                        period.targetMonth().atDay(1), period.rangeEnd()
                ),
                commuteHistoryRepository.findDailyWorkingMinutesByWorkDateBetween(
                        period.rangeStart(), period.rangeEnd()
                )
        );
    }
}
