package com.company.officecommute.service.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmployeeServiceConcurrentTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AnnualLeaveRepository annualLeaveRepository;

    private Long employeeId;
    private final LocalDate targetDate = LocalDate.now().plusDays(10);

    @BeforeEach
    void setup() {
        annualLeaveRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();

        Team team = new Team("teamName", "managerName", 5);
        teamRepository.save(team);
        Employee employee = new Employee("testUser", Role.MEMBER, LocalDate.now(), LocalDate.now());
        Employee savedEmployee = employeeRepository.save(employee);
        employeeId = savedEmployee.getEmployeeId();
        employeeService.updateEmployeeTeamName(new EmployeeUpdateTeamNameRequest(employeeId, "teamName"));
    }

    @Test
    @DisplayName("동시에 같은 날짜 연차 신청시 하나만 성공해야 한다")
    void testConcurrentAnnualLeaveRequest() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    employeeService.enrollAnnualLeave(employeeId, List.of(targetDate));
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        List<AnnualLeave> savedLeaves = annualLeaveRepository.findByEmployeeId(employeeId);

        assertThat(savedLeaves).hasSize(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

}
