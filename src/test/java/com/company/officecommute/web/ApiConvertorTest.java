package com.company.officecommute.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiConvertorTest {

    private final ApiConvertor apiConvertor;

    @Autowired
    ApiConvertorTest(ApiConvertor apiConvertor) {
        this.apiConvertor = apiConvertor;
    }

    @Test
    void test() throws MalformedURLException, URISyntaxException {
        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 5));
        assertThat(numberOfStandardWorkingDays).isEqualTo(21L);
    }

    @Test
    void testCalculateStandardWorkingMinutes() {
        long standardWorkingMinutes = apiConvertor.calculateStandardWorkingMinutes(21L);
        assertThat(standardWorkingMinutes).isEqualTo(21L * 8 * 60);
    }
}
