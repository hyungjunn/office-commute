package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.domain.holiday.HolidayMonthNotLoadedException;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.holiday.HolidayLedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OverTimeServiceTest {

    private static final YearMonth YEAR_MONTH = YearMonth.of(2024, 8);

    @InjectMocks
    private OverTimeService overTimeService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private HolidayLedgerService holidayLedgerService;

    @Test
    @DisplayName("근무 기록 없는 직원도 초과근무 0분으로 포함한다")
    void calculateOverTime_includesEmployeeWithoutCommuteHistory() {
        Team backend = new Team(1L, "백엔드팀", "팀장", 0);
        Employee recordedEmployee = employee(1L, "임형준", backend, "EMP001", "hyungjun@company.com");
        Employee noHistoryEmployee = employee(2L, "김개발", backend, "EMP002", "dev@company.com");
        given(employeeRepository.findAllWithTeam()).willReturn(List.of(recordedEmployee, noHistoryEmployee));
        given(commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(new TotalWorkingMinutes(1L, "EMP001", "임형준", "백엔드팀", 10_180L)));
        givenAugustHolidayLedger();

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(YEAR_MONTH);

        assertThat(responses)
                .extracting(
                        OverTimeCalculateResponse::id,
                        OverTimeCalculateResponse::employeeCode,
                        OverTimeCalculateResponse::name,
                        OverTimeCalculateResponse::teamName,
                        OverTimeCalculateResponse::overTimeMinutes
                )
                .containsExactly(
                        tuple(1L, "EMP001", "임형준", "백엔드팀", 100L),
                        tuple(2L, "EMP002", "김개발", "백엔드팀", 0L)
                );
    }

    @Test
    @DisplayName("근무 기록 있는 직원의 기존 초과근무 계산을 유지한다")
    void calculateOverTime_keepsExistingCalculationForEmployeeWithCommuteHistory() {
        Team backend = new Team(1L, "백엔드팀", "팀장", 0);
        Employee employee = employee(1L, "임형준", backend, "EMP001", "hyungjun@company.com");
        given(employeeRepository.findAllWithTeam()).willReturn(List.of(employee));
        given(commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(new TotalWorkingMinutes(1L, "EMP001", "임형준", "백엔드팀", 10_480L)));
        givenAugustHolidayLedger();

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(YEAR_MONTH);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().overTimeMinutes()).isEqualTo(400L);
    }

    @Test
    @DisplayName("미배정 직원은 근무 기록이 없어도 팀명을 미배정으로 표시한다")
    void calculateOverTime_usesUnassignedTeamNameForEmployeeWithoutTeam() {
        Employee employee = employee(1L, "임형준", null, "EMP001", "hyungjun@company.com");
        given(employeeRepository.findAllWithTeam()).willReturn(List.of(employee));
        given(commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of());
        givenAugustHolidayLedger();

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(YEAR_MONTH);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().teamName()).isEqualTo("미배정");
        assertThat(responses.getFirst().overTimeMinutes()).isZero();
    }

    /**
     * 2024년 8월은 평일 22일이고 광복절(8/15, 목)이 하루 빠져 소정근로일 21일 = 10,080분이다.
     * 기준선은 이제 원장이 준 휴일 날짜에서 도출된다 — 외부 API를 타지 않는다.
     */
    private void givenAugustHolidayLedger() {
        given(holidayLedgerService.getHolidayDates(YEAR_MONTH))
                .willReturn(Set.of(LocalDate.of(2024, 8, 15)));
    }

    @Test
    @DisplayName("원장에 적재되지 않은 달은 계산을 거부한다 — 빈 원장을 공휴일 0개로 읽으면 안 된다")
    void calculateOverTime_refusesUnloadedMonth() {
        given(holidayLedgerService.getHolidayDates(YEAR_MONTH))
                .willThrow(new HolidayMonthNotLoadedException(YEAR_MONTH));

        assertThatThrownBy(() -> overTimeService.calculateOverTime(YEAR_MONTH))
                .isInstanceOf(HolidayMonthNotLoadedException.class);
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
