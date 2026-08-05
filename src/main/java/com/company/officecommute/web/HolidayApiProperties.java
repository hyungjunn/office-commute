package com.company.officecommute.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "public.data.api")
public class HolidayApiProperties {

    private String url;

    // 공공데이터포털이 발급한 "URL 인코딩" 형태의 키를 그대로 보관한다.
    // 디코딩 키를 넣으면 '+'가 서버에서 공백으로 해석되어 인증에 실패한다.
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

}
