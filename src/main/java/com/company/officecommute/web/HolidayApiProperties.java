package com.company.officecommute.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "public.data.api")
public class HolidayApiProperties {

    private String url;

    // 공공데이터포털이 발급한 "URL 인코딩" 형태의 키를 그대로 보관한다.
    // 디코딩 키를 넣으면 '+'가 서버에서 공백으로 해석되어 인증에 실패한다.
    private String serviceKey;

    // 타임아웃은 이 API 전용 값이므로 키·URL과 같은 prefix 가 소유한다.
    // 기본값은 코드에 두어 yml 이 값을 주지 않아도 종전 동작(3s/5s)이 유지된다.
    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(5);

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

}
