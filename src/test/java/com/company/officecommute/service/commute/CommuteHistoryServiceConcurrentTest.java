package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteNotStartedException;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
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
class CommuteHistoryServiceConcurrentTest {

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
    @DisplayName("동시 출근 등록 — 정확히 1건만 저장되고 나머지는 모두 DuplicateWorkOnDate로 분류된다")
    void concurrentRegisterWorkStartTime_exactlyOneSucceeds() throws InterruptedException {
        int threadCount = 100;

        ConcurrencyResult result = runConcurrently(threadCount,
                () -> commuteHistoryService.registerWorkStartTime(testEmployeeId));

        assertThat(commuteHistoryRepository.findAll()).hasSize(1);
        assertThat(result.successCount()).isEqualTo(1);
        // 같은 날 race: existsBy, race-net(validateNoOpenCommute), DB unique 제약 어디서 잡히든
        // 모두 DuplicateWorkOnDate로 분류된다.
        assertThat(result.failures())
                .hasSize(threadCount - 1)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(DuplicateWorkOnDateException.class));
    }

    @Test
    @DisplayName("동시 퇴근 등록 — 조건부 update로 정확히 1건만 성공한다")
    void concurrentRegisterWorkEndTime_exactlyOneSucceeds() throws InterruptedException {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        int threadCount = 20;

        ConcurrencyResult result = runConcurrently(threadCount,
                () -> commuteHistoryService.registerWorkEndTime(testEmployeeId));

        List<CommuteHistory> histories = commuteHistoryRepository.findAll();
        assertThat(histories).hasSize(1);
        assertThat(histories.getFirst().getWorkEndTime()).isNotNull();
        assertThat(result.successCount()).isEqualTo(1);
        // 승자 commit 전에 open commute를 읽은 thread는 조건부 update 0건 → AlreadyEnded,
        // commit 후에 조회한 thread는 open commute가 없어 NotStarted.
        assertThat(result.failures())
                .hasSize(threadCount - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfAny(CommuteAlreadyEndedException.class, CommuteNotStartedException.class));
    }

    // 모든 thread가 start gate 앞에 정렬된 뒤 동시 출발시켜 race 적중 확률을 높인다.
    private ConcurrencyResult runConcurrently(int threadCount, Runnable action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    ready.countDown();
                    startGate.await();
                    action.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        try {
            ready.await();
            startGate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("모든 thread가 30초 내에 완료되어야 한다")
                    .isTrue();
        } finally {
            executor.shutdown();
        }
        return new ConcurrencyResult(successCount.get(), failures);
    }

    private record ConcurrencyResult(int successCount, Queue<Throwable> failures) {
    }
}
