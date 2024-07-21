package com.company.officecommute.web;

public class TestApi implements ApiProperties {
    @Override
    public String combineURL(String solYear, String solMonth) {
        return "http://test-url.com";
    }
}
