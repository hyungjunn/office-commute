package com.company.officecommute.repository.holiday;

import com.company.officecommute.domain.holiday.Holiday;
import com.company.officecommute.domain.holiday.HolidayId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, HolidayId> {

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate startInclusive, LocalDate endInclusive);

    /**
     * 연 단위 재동기화의 1단계 — 해당 범위의 API 행을 통째로 지운다.
     * <p>
     * 엔티티를 하나씩 지우지 않고 벌크 DELETE를 쓰는 이유는 순서 때문이다. Hibernate는 flush에서
     * INSERT를 DELETE보다 먼저 실행하므로, 같은 (날짜, 출처) 키를 지우고 다시 넣는 이 흐름에서
     * 영속성 컨텍스트에 맡기면 아직 살아 있는 행과 부딪혀 UNIQUE 제약을 위반한다.
     * {@code flushAutomatically}가 이 DELETE를 이후 INSERT보다 먼저 DB에 도달시킨다.
     * <p>
     * {@code clearAutomatically}는 그 반대편 함정을 막는다. 벌크 DELETE는 영속성 컨텍스트를
     * 갱신하지 않으므로, 감사 로그를 만들려고 미리 읽어 둔 엔티티가 그대로 관리 상태로 남는다.
     * 그 상태에서 같은 키를 저장하면 merge가 INSERT 대신 UPDATE를 내보내고, 이미 지워진 행을
     * 고치려다 그 날짜가 조용히 사라진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Holiday h
            where h.source = com.company.officecommute.domain.holiday.HolidaySource.API
              and h.holidayDate between :startInclusive and :endInclusive
            """)
    int deleteApiRowsBetween(
            @Param("startInclusive") LocalDate startInclusive,
            @Param("endInclusive") LocalDate endInclusive
    );

    /**
     * 연 단위 재동기화의 2단계 — API가 공휴일로 준 날짜의 (MANUAL, is_holiday=true) 행을 흡수한다.
     * <p>
     * 흡수는 출처 전환이 아니라 삭제다. 그 행의 존재 이유가 "API가 아직 안 준 날을 대신 채운다"이므로
     * API 행이 생긴 순간 소임이 끝난다. 이름 갱신도 여기서 자동으로 해결된다 — 남는 건 API 행뿐이다.
     * <p>
     * 부정 오버라이드(is_holiday=false)는 정반대 의도이므로 건드리지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Holiday h
            where h.source = com.company.officecommute.domain.holiday.HolidaySource.MANUAL
              and h.isHoliday = true
              and h.holidayDate in :dates
            """)
    int deleteManualHolidaysOn(@Param("dates") Collection<LocalDate> dates);
}
