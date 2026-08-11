package com.company.officecommute.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 수신자·발신자는 전부 설정값이다. 코드에 박으면 사람이 바뀔 때마다 재배포가 필요하고,
 * 저장소에 실주소가 남는다.
 */
@Configuration
@ConfigurationProperties(prefix = "report.mail")
public class ReportMailProperties {

    /** 발신 주소. */
    private String from;

    /** 대표 — 정상 리포트의 유일한 수신자. */
    private String ceo;

    /** 근태 관리자 — 보류·실패 알림 수신자. 복수 가능(쉼표 구분 환경변수). */
    private List<String> managers = List.of();

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getCeo() {
        return ceo;
    }

    public void setCeo(String ceo) {
        this.ceo = ceo;
    }

    public List<String> getManagers() {
        return managers;
    }

    public void setManagers(List<String> managers) {
        this.managers = List.copyOf(managers);
    }
}
