package com.company.officecommute.service.overtime;

import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OverTimeSnapshotReaderTest {

    // 2024-08-01은 목요일 — 1일이 속한 주가 7월에 걸친다
    private static final OverTimePeriod AUGUST = new OverTimePeriod(YearMonth.of(2024, 8));
    // 2024-07-01은 월요일 — 주 경계와 월 경계가 일치한다
    private static final OverTimePeriod JULY = new OverTimePeriod(YearMonth.of(2024, 7));

    @InjectMocks
    private OverTimeSnapshotReader overTimeSnapshotReader;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("월 1일이 속한 주가 전월에 걸치면 근무 기록 조회를 그 주의 월요일까지 넓힌다")
    void read_extendsCommuteRangeToStraddlingWeek() {
        overTimeSnapshotReader.read(AUGUST);

        // 8/1(목)이 속한 주의 월요일은 7/29 — 그 주의 40시간 판정에 전월 기록이 필요하다
        then(commuteHistoryRepository).should()
                .findDailyWorkingMinutesByWorkDateBetween(LocalDate.of(2024, 7, 29), LocalDate.of(2024, 8, 31));
    }

    @Test
    @DisplayName("리포트 대상자는 스필오버 주가 아니라 월 경계(1일~말일)의 재직 겹침으로 조회한다")
    void read_queriesEmployeesByMonthBoundsNotSpilloverStart() {
        // 7/31 퇴사자는 8월 리포트 대상이 아니다 — 스필오버 주(7/29~)의 근무는 7월 리포트가 이미 집계했다.
        // 대상자 조회까지 7/29로 넓히면 그 퇴사자가 8월 리포트에 0분 행으로 되살아난다.
        overTimeSnapshotReader.read(AUGUST);

        then(employeeRepository).should()
                .findAllWithTeamEmployedBetween(LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));
    }

    @Test
    @DisplayName("스냅샷 조회는 읽기 전용 트랜잭션이다 — 두 쿼리 사이의 비일관 스냅샷을 막는 유일한 근거다")
    void read_isReadOnlyTransactional() throws NoSuchMethodException {
        Transactional transactional = OverTimeSnapshotReader.class
                .getMethod("read", OverTimePeriod.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("월 1일이 월요일이면 범위 확장 없이 그 달만 조회한다")
    void read_singleMonthWhenWeekAlignsWithMonth() {
        overTimeSnapshotReader.read(JULY);

        then(commuteHistoryRepository).should()
                .findDailyWorkingMinutesByWorkDateBetween(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 31));
        then(employeeRepository).should()
                .findAllWithTeamEmployedBetween(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 31));
    }
}
