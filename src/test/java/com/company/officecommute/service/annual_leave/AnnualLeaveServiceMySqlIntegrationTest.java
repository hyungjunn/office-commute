package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveDuplicateException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willReturn;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never"
})
@Testcontainers(disabledWithoutDocker = true)
class AnnualLeaveServiceMySqlIntegrationTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2099, 7, 13);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired private AnnualLeaveService annualLeaveService;
    @MockitoSpyBean private AnnualLeaveRepository annualLeaveRepository;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TeamRepository teamRepository;

    private Long employeeId;

    @BeforeEach
    void setup() {
        commuteHistoryRepository.deleteAll();
        annualLeaveRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();

        Team team = teamRepository.save(Team.register("MySQL 연차 테스트팀", null, 0));
        Employee employee = Employee.register(
                "MySQL 연차 테스트직원",
                Role.MEMBER,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2024, 1, 1),
                "MYSQL01",
                "mysql-leave@company.com",
                "password123",
                "Asia/Seoul",
                team
        );
        employeeId = employeeRepository.save(employee).getEmployeeId();
    }

    @Test
    @DisplayName("MySQL의 연차 유니크 제약 위반을 AnnualLeaveDuplicateException으로 변환한다")
    void translatesMySqlDuplicateConstraintViolation() {
        annualLeaveRepository.saveAndFlush(new AnnualLeave(employeeId, TARGET_DATE));
        willReturn(List.of())
                .given(annualLeaveRepository)
                .findByEmployeeId(employeeId);

        assertThatThrownBy(() -> annualLeaveService.enrollAnnualLeave(
                employeeId, List.of(TARGET_DATE)))
                .isInstanceOf(AnnualLeaveDuplicateException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
