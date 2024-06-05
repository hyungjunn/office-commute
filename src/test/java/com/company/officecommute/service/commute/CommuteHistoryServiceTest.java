package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.service.employee.EmployeeDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.company.officecommute.service.employee.Employees.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommuteHistoryServiceTest {

    @InjectMocks
    private CommuteHistoryService commuteHistoryService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private CommuteHistoryDomainService commuteHistoryDomainService;

    @Mock
    private EmployeeDomainService employeeDomainService;


    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    @BeforeEach
    void setUp() {
        workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    }

    @Test
    void testRegisterWorkStartTime() {
        BDDMockito.given(employeeDomainService.findEmployeeById(1L))
                .willReturn(employee);
        BDDMockito.doNothing().when(commuteHistoryDomainService)
                .distinguishItIsPossibleToWork(1L);

        commuteHistoryService.registerWorkStartTime(1L);

        verify(commuteHistoryRepository).save(any(CommuteHistory.class));
    }

    @Test
    void testRegisterWorkEndTime() {
        // given
        BDDMockito.given(employeeDomainService.findEmployeeById(1L))
                .willReturn(employee);
        BDDMockito.given(commuteHistoryDomainService.findFirstByDomainService(1L))
                .willReturn(new CommuteHistory(1L, 1L, workStartTime, null, 0));

        CommuteHistory expectedCommuteHistory = new CommuteHistory(1L, 1L, workStartTime, workEndTime, 10L * 60);
        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(expectedCommuteHistory);

        // when
        commuteHistoryService.registerWorkEndTime(1L, workEndTime);

        // then
        ArgumentCaptor<CommuteHistory> commuteHistoryCaptor = ArgumentCaptor.forClass(CommuteHistory.class);
        verify(commuteHistoryRepository).save(commuteHistoryCaptor.capture());
        CommuteHistory savedCommuteHistory = commuteHistoryCaptor.getValue();

        assertThat(savedCommuteHistory.getWorkEndTime()).isEqualTo(expectedCommuteHistory.getWorkEndTime());
    }
}
