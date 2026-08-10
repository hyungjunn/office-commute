package com.company.officecommute.config;

import com.company.officecommute.web.HolidayApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 유일한 소비자는 {@code HolidayApiClient} 다. 따라서 타임아웃도 그 API 의 설정
 * ({@code public.data.api.*}) 에서 읽는다 — 값이 yml/환경변수로 나와 있어 운영에서
 * 재배포 없이 조정할 수 있다.
 */
@Configuration
public class RestTemplateConfig {

    private final HolidayApiProperties holidayApiProperties;

    public RestTemplateConfig(HolidayApiProperties holidayApiProperties) {
        this.holidayApiProperties = holidayApiProperties;
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(holidayApiProperties.getConnectTimeout());
        factory.setReadTimeout(holidayApiProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);

        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        messageConverters.add(new MappingJackson2XmlHttpMessageConverter());
        restTemplate.setMessageConverters(messageConverters);

        return restTemplate;
    }
}
