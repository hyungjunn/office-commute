-- 공휴일 원장(system of record). 소정근로일 계산은 이 테이블만 읽는다.
CREATE TABLE holiday (
    holiday_id   BIGINT       NOT NULL AUTO_INCREMENT,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    source       VARCHAR(16)  NOT NULL,
    is_holiday   BIT(1)       NOT NULL,
    PRIMARY KEY (holiday_id),
    -- 한 날짜에 API 행과 MANUAL 행이 공존해야 하므로 유니크는 (날짜, 출처) 단위다.
    CONSTRAINT uk_holiday_date_source UNIQUE (holiday_date, source),
    -- 부정 오버라이드("공휴일 아님")는 MANUAL 전용이다.
    CONSTRAINT ck_holiday_api_is_holiday CHECK (source <> 'API' OR is_holiday = TRUE)
);
