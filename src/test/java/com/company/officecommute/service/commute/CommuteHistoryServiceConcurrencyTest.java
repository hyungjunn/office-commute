package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CommuteHistoryServiceConcurrencyTest {

    @Autowired
    private CommuteHistoryService commuteHistoryService;

    @Autowired
    private CommuteHistoryRepository commuteHistoryRepository;

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
}
