package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeaveDuplicateException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnnualLeaveServiceConcurrentTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2099, 7, 13);

    @Autowired private AnnualLeaveService annualLeaveService;
    @Autowired private AnnualLeaveRepository annualLeaveRepository;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TeamRepository teamRepository;

    private Long employeeId;

    @BeforeEach
    void setup() {
        deleteTestData();

        Team team = teamRepository.save(Team.register("연차 동시성 테스트팀", null, 0));
        Employee employee = Employee.register(
                "연차 동시성 테스트직원",
                Role.MEMBER,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2024, 1, 1),
                "LEAVE01",
                "leave-concurrency@company.com",
                "password123",
                "Asia/Seoul",
                team
        );
        employeeId = employeeRepository.save(employee).getEmployeeId();
    }

    @AfterEach
    void cleanup() {
        deleteTestData();
    }

    @Test
    @DisplayName("같은 직원의 같은 날짜 연차 신청이 동시에 들어오면 하나만 성공하고 하나는 중복 예외가 된다")
    void concurrentEnrollment_translatesLosingRequestToDuplicate() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    ready.countDown();
                    startGate.await();
                    annualLeaveService.enrollAnnualLeave(employeeId, List.of(TARGET_DATE));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(annualLeaveRepository.findByEmployeeId(employeeId)).hasSize(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures)
                .singleElement()
                .isInstanceOf(AnnualLeaveDuplicateException.class);
    }

    private void deleteTestData() {
        commuteHistoryRepository.deleteAll();
        annualLeaveRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();
    }
}
