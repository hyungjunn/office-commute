package com.company.officecommute.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class WebConfigTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    // AuthInterceptor 가 /** 에 걸려 있던 시절, 비로그인 상태에서 SPA 정적 자산(/assets/*.js)
    // 요청이 401 이 되어 로그인 페이지 자체가 로드되지 않았다 (docs/DEPLOYMENT.md §1-2).
    // 인터셉터는 /api/** 에만 적용되어야 한다.
    @Test
    @DisplayName("비로그인 상태에서 비-API 경로(정적 자산)는 인터셉터에 걸리지 않는다 — 401 이 아닌 404")
    void nonApiPathBypassesAuthInterceptor() {
        assertThat(mockMvcTester.get().uri("/assets/index-abc123.js"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("비로그인 상태에서 /api/** 는 여전히 401")
    void apiPathStillRequiresAuthentication() {
        assertThat(mockMvcTester.get().uri("/api/team"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
