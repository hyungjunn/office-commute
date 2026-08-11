package com.company.officecommute.domain.report;

public enum DispatchStatus {

    /** 어떤 실행이 이 달을 선점해 진행 중이다. 리스 시간이 지나면 회수 대상이 된다. */
    IN_PROGRESS,

    /** 대표에게 발송 완료. 종착 상태 — 이 달은 다시 발송되지 않는다. */
    SENT,

    /** 시도했으나 실패. 재시도 창 안이면 다음 시도가 다시 집어간다. */
    FAILED
}
