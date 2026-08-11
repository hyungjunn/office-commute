package com.company.officecommute.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 프로젝트의 첫 스케줄러 도입.
 * <p>
 * {@code @SpringBootApplication}에 붙이지 않고 별도 설정으로 분리한다 — 배치 활성화가
 * 애플리케이션 부트스트랩과 한 덩어리가 되면 끄고 켜는 지점이 사라진다.
 * 실제 배치 빈은 {@code @Profile("prod")}라 dev/test 에서는 등록되지 않는다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
