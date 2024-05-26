package com.company.officecommute.controller.commute;

import com.company.officecommute.dto.commute.request.WorkEndTimeRequest;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommuteHistoryController {

    private final CommuteHistoryService commuteHistoryService;

    public CommuteHistoryController(CommuteHistoryService commuteHistoryService) {
        this.commuteHistoryService = commuteHistoryService;
    }

    @PostMapping("/commute")
    public void registerWorkStartTime(@RequestBody Long employeeId) {
        commuteHistoryService.registerWorkStartTime(employeeId);
    }

    @PutMapping("/commute")
    public void registerWorkEndTime(@RequestBody WorkEndTimeRequest request) {
        commuteHistoryService.registerWorkEndTime(request);
    }
}
