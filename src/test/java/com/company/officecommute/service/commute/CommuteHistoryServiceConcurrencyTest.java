package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class CommuteHistoryServiceConcurrencyTest {

    @Autowired
    private CommuteHistoryService commuteHistoryService;

    @Autowired
    private CommuteHistoryRepository commuteHistoryRepository;

    @BeforeEach
    void setup() {
        commuteHistoryRepository.deleteAll();
    }

    @Test
    @DisplayName("동시 출근 등록 테스트 - H2 DB")
    void testConcurrentRegisterWorkStartTime_H2DB() throws InterruptedException {
        Long employeeId = 1L;
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    commuteHistoryService.registerWorkStartTime(employeeId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        List<CommuteHistory> histories = commuteHistoryRepository.findAll();
        assertThat(histories).hasSize(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    @DisplayName("이전 출근 미완료시 비즈니스 검증 실패")
    void testBusinessValidationFailure() {
        // 현재 테스트 - 비즈니스 로직 검증
        commuteHistoryService.registerWorkStartTime(1L);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이전 근무가 아직 종료되지 않았습니다.");
    }

    @Test
    @DisplayName("퇴근 후 같은 날 재출근시 DB 제약조건 위반")
    void testDatabaseConstraintViolation() {
        // DB Unique Constraint 테스트
        commuteHistoryService.registerWorkStartTime(1L);
        commuteHistoryService.registerWorkEndTime(1L, ZonedDateTime.now());

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
