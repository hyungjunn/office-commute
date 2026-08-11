package com.company.officecommute.mail;

/**
 * 메일 구성·설정 문제. 발송 유스케이스는 이것을 {@code MAIL_SEND_FAILED}로 분류해
 * 실패로 기록한다 — 스케줄러 스레드를 죽이지 않는다.
 */
public class ReportMailException extends RuntimeException {

    public ReportMailException(String message) {
        super(message);
    }

    public ReportMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
