package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.dto.commute.request.WorkEndTimeRequest;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.company.officecommute.service.employee.Employees.employee;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommuteHistoryServiceTest {

    @InjectMocks
    private CommuteHistoryService commuteHistoryService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private EmployeeRepository employeeRepository;


    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    @BeforeEach
    void setUp() {
        workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    }

    @Test
    void testRegisterWorkStartTime() {
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(new CommuteHistory(1L, 1L, workStartTime, null, 0));

        commuteHistoryService.registerWorkStartTime(1L);

        verify(commuteHistoryRepository).save(any(CommuteHistory.class));
    }

    @Test
    void testRegisterWorkEndTime() {
        WorkEndTimeRequest request = new WorkEndTimeRequest(1L, workEndTime);

        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(new CommuteHistory(1L, 1L, workStartTime, null, 0)));

        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(new CommuteHistory(1L, 1L, workStartTime, workEndTime, 10L * 60));

        commuteHistoryService.registerWorkEndTime(request);

        verify(commuteHistoryRepository).save(any(CommuteHistory.class));
    }
}
