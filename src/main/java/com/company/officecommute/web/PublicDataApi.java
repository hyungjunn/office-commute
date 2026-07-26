package com.company.officecommute.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "public.data.api")
public class PublicDataApi implements ApiProperties {

    // 월 단위 조회지만 numOfRows 기본값(10)에 의존하면 대체공휴일이 겹치는 달에서 응답이 잘릴 수 있다.
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
    public String combineURL(String solYear, String solMonth) {
        return this.url + "?serviceKey=" + this.serviceKey
                + "&solYear=" + solYear
                + "&solMonth=" + solMonth
                + "&numOfRows=" + NUM_OF_ROWS;
    }

}
