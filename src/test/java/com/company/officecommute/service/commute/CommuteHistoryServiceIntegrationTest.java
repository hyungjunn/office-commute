package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.commute.PreviousCommuteNotEndedException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CommuteHistoryServiceIntegrationTest {

    @Autowired private CommuteHistoryService commuteHistoryService;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TeamRepository teamRepository;

    private Long testEmployeeId;

    @BeforeEach
    void setup() {
        commuteHistoryRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();

        Team team = Team.register("테스트팀", null, 0);
        teamRepository.save(team);

        Employee employee = new EmployeeBuilder()
                .withTeam(team)
                .withName("테스트직원")
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("TEST001")
                .withEmail("test@company.com")
                .withPassword("password123")
                .build();
        Employee savedEmployee = employeeRepository.save(employee);
        testEmployeeId = savedEmployee.getEmployeeId();
    }

    @AfterEach
    void cleanup() {
        commuteHistoryRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 날 미완료 근무 중 재출근시 DuplicateWorkOnDateException (광클 방어)")
    void sameDayDoubleStartWhilePreviousOpenThrowsDuplicateWorkOnDate() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");
    }

    @Test
    @DisplayName("어제 미완료 근무 + 오늘 첫 출근시 PreviousCommuteNotEndedException")
    void crossDayPreviousOpenStillTriggersPreviousCommuteNotEnded() {
        ZonedDateTime yesterdayStart = ZonedDateTime.now()
                .minusDays(1)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        commuteHistoryRepository.save(CommuteHistoryFixture.open(
                null, testEmployeeId, yesterdayStart, yesterdayStart.getZone()));

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(PreviousCommuteNotEndedException.class)
                .hasMessage("이전 근무가 아직 종료되지 않았습니다.");
    }

    @Test
    @DisplayName("미래 연차 기록이 있어도 오늘 실제 출근과 퇴근이 가능하다")
    void testFutureAnnualLeaveDoesNotBlockTodayWorkStartAndEnd() {
        commuteHistoryRepository.save(CommuteHistoryFixture.annualLeave(
                testEmployeeId,
                LocalDate.now().plusDays(10),
                ZonedDateTime.now().getZone()
        ));

        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThat(commuteHistoryRepository.findAll()).hasSize(2);
        assertThat(commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(testEmployeeId))
                .isEmpty();
    }

    @Test
    @DisplayName("퇴근 후 같은 날 재출근시 DuplicateWorkOnDateException")
    void sameDaySecondStartThrowsDuplicateWorkOnDate() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");
    }
}
