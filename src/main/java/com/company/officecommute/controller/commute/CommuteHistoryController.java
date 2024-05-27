package com.company.officecommute.controller.commute;

import com.company.officecommute.dto.commute.request.WorkDurationPerDateRequest;
import com.company.officecommute.dto.commute.request.WorkEndTimeRequest;
import com.company.officecommute.dto.commute.request.WorkStartTimeRequest;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;

@RestController
public class CommuteHistoryController {

    private final CommuteHistoryService commuteHistoryService;

    public CommuteHistoryController(CommuteHistoryService commuteHistoryService) {
        this.commuteHistoryService = commuteHistoryService;
    }

    @PostMapping("/commute")
    public void registerWorkStartTime(@RequestBody WorkStartTimeRequest request) {
        commuteHistoryService.registerWorkStartTime(request.employeeId());
    }

    @PutMapping("/commute")
    public void registerWorkEndTime(@RequestBody WorkEndTimeRequest request) {
        commuteHistoryService.registerWorkEndTime(request.employeeId(), ZonedDateTime.now());
    }

    @GetMapping("/commute")
    public WorkDurationPerDateResponse getWorkDurationPerDate(WorkDurationPerDateRequest request) {
        return commuteHistoryService.getWorkDurationPerDate(request.employeeId(), request.yearMonth());
    }

}
