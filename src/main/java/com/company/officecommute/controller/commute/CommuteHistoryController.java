package com.company.officecommute.controller.commute;

import com.company.officecommute.dto.commute.request.WorkDurationPerDateRequest;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.service.commute.CommuteHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;

@RestController
public class CommuteHistoryController {

    private final Logger logger = LoggerFactory.getLogger(CommuteHistoryController.class);

    private final CommuteHistoryService commuteHistoryService;

    public CommuteHistoryController(CommuteHistoryService commuteHistoryService) {
        this.commuteHistoryService = commuteHistoryService;
    }

    @PostMapping("/commute")
    public void registerWorkStartTime(HttpServletRequest request) {
        Long employeeId = (Long) request.getAttribute("employeeId");
        commuteHistoryService.registerWorkStartTime(employeeId);
    }

    @PutMapping("/commute")
    public void registerWorkEndTime(HttpServletRequest request) {
        Long employeeId = (Long) request.getAttribute("employeeId");
        commuteHistoryService.registerWorkEndTime(employeeId, ZonedDateTime.now());
    }

    @GetMapping("/commute")
    public WorkDurationPerDateResponse getWorkDurationPerDate(
            HttpServletRequest request,
            WorkDurationPerDateRequest dateRequest) {
        Long employeeId = (Long) request.getAttribute("employeeId");
        return commuteHistoryService.getWorkDurationPerDate(employeeId, dateRequest.yearMonth());
    }
}
