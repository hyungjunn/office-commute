package com.company.officecommute.controller.commute;

import com.company.officecommute.dto.commute.request.WorkStartTimeRequest;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommuteHistoryController {

    private final CommuteHistoryService commuteHistoryService;

    public CommuteHistoryController(CommuteHistoryService commuteHistoryService) {
        this.commuteHistoryService = commuteHistoryService;
    }

    @PostMapping("/commute/start")
    public void registerWorkStartTime(@RequestBody WorkStartTimeRequest request) {
        commuteHistoryService.registerWorkStartTime(request);
    }
}
