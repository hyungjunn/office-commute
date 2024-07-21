package com.company.officecommute.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "public.data.api")
public class PublicDataApi implements ApiProperties {

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
        return this.url + "?serviceKey=" + this.serviceKey + "&solYear=" + solYear + "&solMonth=" + solMonth;
    }

}
