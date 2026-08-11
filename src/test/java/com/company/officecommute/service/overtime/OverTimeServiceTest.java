package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.web.HolidayApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OverTimeServiceTest {

    // 2024-08-01은 목요일 — 1일이 속한 주가 7월에 걸친다
    private static final YearMonth AUGUST = YearMonth.of(2024, 8);
    // 2024-07-01은 월요일 — 주 경계와 월 경계가 일치한다
    private static final YearMonth JULY = YearMonth.of(2024, 7);

    @InjectMocks
    private OverTimeService overTimeService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private OverTimeSnapshotReader overTimeSnapshotReader;

    @Mock
    private HolidayApiClient holidayApiClient;

    @Test
    @DisplayName("근무 기록 없는 직원도 모든 트랙 0분으로 포함한다")
    void calculateOverTime_includesEmployeeWithoutCommuteHistory() {
        Team backend = new Team(1L, "백엔드팀", "팀장", 0);
        Employee recordedEmployee = employee(1L, "임형준", backend, "EMP001", "hyungjun@company.com");
        Employee noHistoryEmployee = employee(2L, "김개발", backend, "EMP002", "dev@company.com");
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());
        // 7/1(월) 12시간 근무 — 일별 8시간 초과 4시간
        given(overTimeSnapshotReader.read(JULY)).willReturn(new OverTimeSnapshot(
                List.of(recordedEmployee, noHistoryEmployee),
                List.of(new DailyWorkingMinutes(1L, LocalDate.of(2024, 7, 1), 720L))
        ));

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(JULY);

        assertThat(responses)
                .extracting(
                        OverTimeCalculateResponse::id,
                        OverTimeCalculateResponse::employeeCode,
                        OverTimeCalculateResponse::name,
                        OverTimeCalculateResponse::teamName,
                        OverTimeCalculateResponse::overTimeMinutes,
                        OverTimeCalculateResponse::holidayWithin8HoursMinutes,
                        OverTimeCalculateResponse::holidayExceeding8HoursMinutes
                )
                .containsExactly(
                        tuple(1L, "EMP001", "임형준", "백엔드팀", 240L, 0L, 0L),
                        tuple(2L, "EMP002", "김개발", "백엔드팀", 0L, 0L, 0L)
                );
    }

    @Test
    @DisplayName("미배정 직원은 근무 기록이 없어도 팀명을 미배정으로 표시한다")
    void calculateOverTime_usesUnassignedTeamNameForEmployeeWithoutTeam() {
        Employee employee = employee(1L, "임형준", null, "EMP001", "hyungjun@company.com");
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());
        given(overTimeSnapshotReader.read(JULY))
                .willReturn(new OverTimeSnapshot(List.of(employee), List.of()));

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(JULY);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().teamName()).isEqualTo("미배정");
        assertThat(responses.getFirst().overTimeMinutes()).isZero();
    }

    @Test
    @DisplayName("월 1일이 속한 주가 전월에 걸치면 전월 공휴일도 함께 가져온다")
    void calculateOverTime_fetchesStraddlingMonthHolidays() {
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());
        given(overTimeSnapshotReader.read(AUGUST)).willReturn(emptySnapshot());

        overTimeService.calculateOverTime(AUGUST);

        // 8/1(목)이 속한 주의 월요일은 7/29 — 그 주의 휴일근로 분류에 전월 공휴일이 필요하다
        then(holidayApiClient).should().getHolidays(AUGUST);
        then(holidayApiClient).should().getHolidays(JULY);
    }

    @Test
    @DisplayName("월 1일이 월요일이면 해당 월 공휴일만 가져온다")
    void calculateOverTime_singleMonthHolidaysWhenWeekAlignsWithMonth() {
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());
        given(overTimeSnapshotReader.read(JULY)).willReturn(emptySnapshot());

        overTimeService.calculateOverTime(JULY);

        then(holidayApiClient).should().getHolidays(JULY);
        then(holidayApiClient).should(never()).getHolidays(YearMonth.of(2024, 6));
    }

    @Test
    @DisplayName("공휴일 외부 호출은 DB 읽기 스냅샷보다 먼저 끝난다 — 트랜잭션이 외부 API를 기다리지 않는다")
    void calculateOverTime_callsHolidayApiBeforeOpeningSnapshot() {
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());
        given(overTimeSnapshotReader.read(JULY)).willReturn(emptySnapshot());

        overTimeService.calculateOverTime(JULY);

        InOrder inOrder = Mockito.inOrder(holidayApiClient, overTimeSnapshotReader);
        inOrder.verify(holidayApiClient).getHolidays(JULY);
        inOrder.verify(overTimeSnapshotReader).read(JULY);
    }

    @Test
    @DisplayName("퇴근 미마감 검사는 계산이 소비하는 범위와 같다 — 전월 스필오버 일자의 미마감도 잡힌다")
    void countUnclosedCommutes_coversSpilloverWeek() {
        // 전월 말(7/29) 미마감 기록은 0분으로 8월 첫 주의 40시간 기반을 깎아 과소 집계를 만든다.
        // 검사 범위가 8/1~8/31이면 이 기록을 놓쳐 "미마감 0건"이라는 거짓 완결 보증이 나간다.
        given(commuteHistoryRepository.countByWorkDateBetweenAndWorkEndTimeIsNull(
                LocalDate.of(2024, 7, 29), LocalDate.of(2024, 8, 31))).willReturn(1L);

        assertThat(overTimeService.countUnclosedCommutes(AUGUST)).isEqualTo(1L);
    }

    @Test
    @DisplayName("미마감 목록 조회 범위는 건수 조회 범위와 정확히 같다 — 어긋나면 '건수 3건, 목록 2건'이 된다")
    void findUnclosedCommutes_usesSameRangeAsCount() {
        LocalDate rangeStart = LocalDate.of(2024, 7, 29); // 8/1(목)이 속한 주의 월요일
        LocalDate rangeEnd = LocalDate.of(2024, 8, 31);
        given(commuteHistoryRepository.countByWorkDateBetweenAndWorkEndTimeIsNull(rangeStart, rangeEnd))
                .willReturn(1L);
        given(commuteHistoryRepository.findUnclosedByWorkDateBetween(rangeStart, rangeEnd))
                .willReturn(List.of(new UnclosedCommute("EMP001", "임형준", LocalDate.of(2024, 8, 31))));

        long count = overTimeService.countUnclosedCommutes(AUGUST);
        List<UnclosedCommute> unclosed = overTimeService.findUnclosedCommutes(AUGUST);

        // 두 스텁이 같은 인자로 걸려 있으므로, 범위가 갈라지면 둘 중 하나가 기본값(0/빈 목록)으로 떨어진다
        assertThat(count).isEqualTo(1L);
        assertThat(unclosed).hasSize(1);
    }

    @Test
    @DisplayName("일요일·공휴일 근무는 휴일근로 트랙으로 응답에 실린다")
    void calculateOverTime_populatesHolidayTracks() {
        Team backend = new Team(1L, "백엔드팀", "팀장", 0);
        Employee employee = employee(1L, "임형준", backend, "EMP001", "hyungjun@company.com");
        given(holidayApiClient.getHolidays(JULY)).willReturn(Set.of(LocalDate.of(2024, 7, 3)));
        given(overTimeSnapshotReader.read(JULY)).willReturn(new OverTimeSnapshot(
                List.of(employee),
                List.of(
                        new DailyWorkingMinutes(1L, LocalDate.of(2024, 7, 3), 600L), // 공휴일 10h
                        new DailyWorkingMinutes(1L, LocalDate.of(2024, 7, 7), 360L)  // 일요일 6h
                )
        ));

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(JULY);

        assertThat(responses.getFirst().overTimeMinutes()).isZero();
        assertThat(responses.getFirst().holidayWithin8HoursMinutes()).isEqualTo(840L); // 480 + 360
        assertThat(responses.getFirst().holidayExceeding8HoursMinutes()).isEqualTo(120L);
    }

    private OverTimeSnapshot emptySnapshot() {
        return new OverTimeSnapshot(List.of(), List.of());
    }

    private Employee employee(Long id, String name, Team team, String employeeCode, String email) {
        return new EmployeeBuilder()
                .withId(id)
                .withTeam(team)
                .withName(name)
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode(employeeCode)
                .withEmail(email)
                .withPassword("password123")
                .build();
    }
}
