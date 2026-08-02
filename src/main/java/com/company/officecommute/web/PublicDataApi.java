package com.company.officecommute.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "public.data.api")
public class PublicDataApi implements ApiProperties {

    // 연 단위 조회. 한국의 연간 공휴일은 대체공휴일을 합쳐도 20건 내외라 100이면 한 페이지에 다 온다.
    // 기본값(10)에 의존하면 응답이 잘리고, 잘림은 곧 공휴일 누락 → 초과근무 과소 집계다.
    private static final int NUM_OF_ROWS = 100;

    private String url;
    private String serviceKey;

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

    @Override
    public String combineURL(String solYear) {
        return this.url + "?serviceKey=" + this.serviceKey
                + "&solYear=" + solYear
                + "&numOfRows=" + NUM_OF_ROWS;
    }

}
