package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.dto.commute.request.WorkStartTimeRequest;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommuteHistoryServiceTest {

    @InjectMocks
    private CommuteHistoryService commuteHistoryService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Test
    void testRegisterWorkStartTime() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        WorkStartTimeRequest request = new WorkStartTimeRequest(1L, workStartTime);

        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(new CommuteHistory(1L, 1L, workStartTime, null, 0));

        commuteHistoryService.registerWorkStartTime(request);

        verify(commuteHistoryRepository).save(any(CommuteHistory.class));
    }
}
