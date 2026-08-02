package com.company.officecommute.controller.holiday;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.holiday.response.HolidaySyncResponse;
import com.company.officecommute.service.holiday.HolidaySyncService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

@RestController
@Validated
public class HolidayController {

    private final HolidaySyncService holidaySyncService;

    public HolidayController(HolidaySyncService holidaySyncService) {
        this.holidaySyncService = holidaySyncService;
    }

    /**
     * 지정한 연도를 즉시 동기화한다. 정기 동기화가 도는 범위(올해~+2년) 밖의 과거 연도를 채우는
     * backfill 통로이자, 임시공휴일 지정 직후 다음 새벽까지 기다리지 않기 위한 수단이다.
     * <p>
     * 정기 동기화와 달리 실패를 삼키지 않는다 — 관리자가 직접 부른 요청이므로 결과를 알아야 한다.
     */
    @ManagerOnly
    @PostMapping("/holiday/sync")
    public HolidaySyncResponse syncHolidays(
            @RequestParam @Min(2000) @Max(2100) int year
    ) {
        int holidayCount = holidaySyncService.syncYear(Year.of(year));
        return new HolidaySyncResponse(year, holidayCount);
    }
}
