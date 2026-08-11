package com.company.officecommute.domain.report;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * {@code YearMonth} ↔ {@code CHAR(7)} 'yyyy-MM'.
 * {@link YearMonth#toString()}과 {@link YearMonth#parse(CharSequence)}가 이미 ISO 'yyyy-MM'
 * 라운드트립을 보장하므로 별도 포맷터를 두지 않는다.
 */
@Converter
public class YearMonthAttributeConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        return dbData == null ? null : YearMonth.parse(dbData);
    }
}
