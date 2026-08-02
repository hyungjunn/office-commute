package com.company.officecommute.web;

public interface ApiProperties {

    /**
     * 공휴일 조회 URL을 만든다. solMonth를 지정하지 않으면 포털이 그 해 전체를 반환한다 —
     * 적재 단위가 연이므로 월은 받지 않는다.
     */
    String combineURL(String solYear);
}
