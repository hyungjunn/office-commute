package com.company.officecommute.repository.holiday;

import com.company.officecommute.domain.holiday.Holiday;
import com.company.officecommute.domain.holiday.HolidayId;
import com.company.officecommute.domain.holiday.HolidaySource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
class HolidayRepositoryTest {

    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("(날짜, 출처)를 복합 PK로 저장하고 조회한다")
    void savesWithCompositePrimaryKey() {
        holidayRepository.save(Holiday.fromApi(LocalDate.of(2026, 1, 1), "1월1일"));
        flushAndClear();

        Optional<Holiday> found = holidayRepository.findById(
                new HolidayId(LocalDate.of(2026, 1, 1), HolidaySource.API));

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("1월1일");
        assertThat(found.get().getSource()).isEqualTo(HolidaySource.API);
        assertThat(found.get().isHoliday()).isTrue();
    }

    @Test
    @DisplayName("수동 등록 행도 출처가 보존된 채 저장된다")
    void savesManualRow() {
        holidayRepository.save(Holiday.manualHoliday(LocalDate.of(2026, 6, 3), "제21대 대통령 선거(사후 지정)"));
        flushAndClear();

        Holiday found = holidayRepository.findById(
                new HolidayId(LocalDate.of(2026, 6, 3), HolidaySource.MANUAL)).orElseThrow();

        assertThat(found.isManual()).isTrue();
        assertThat(found.getSource()).isEqualTo(HolidaySource.MANUAL);
    }

    @Test
    @DisplayName("부정 오버라이드 행은 is_holiday=false로 왕복한다")
    void persistsNegativeOverride() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        holidayRepository.save(Holiday.manualWorkingDay(date, "정상 근무(전사 공지)"));
        flushAndClear();

        Holiday found = holidayRepository.findById(new HolidayId(date, HolidaySource.MANUAL)).orElseThrow();

        assertThat(found.isHoliday()).isFalse();
        assertThat(found.isNegativeOverride()).isTrue();
    }

    @Test
    @DisplayName("월 범위 조회는 경계 날짜를 포함하고 날짜순으로 반환한다")
    void findsWithinMonthRangeInclusive() {
        holidayRepository.saveAll(List.of(
                Holiday.fromApi(LocalDate.of(2026, 1, 31), "전월 말일"),
                Holiday.fromApi(LocalDate.of(2026, 2, 28), "당월 말일"),
                Holiday.fromApi(LocalDate.of(2026, 2, 1), "당월 1일"),
                Holiday.fromApi(LocalDate.of(2026, 3, 1), "익월 1일")
        ));
        flushAndClear();

        List<Holiday> found = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(found).extracting(Holiday::getHolidayDate)
                .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("공휴일이 없는 달은 빈 목록을 반환한다 — '적재 안 됨'과 구분되지 않으므로 월 적재 마커가 필요하다")
    void emptyMonthIsIndistinguishableFromNotLoaded() {
        List<Holiday> found = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(found).isEmpty();
    }

    /**
     * 식별자에 출처가 들어간 덕분에 동기화가 API 행을 넣어도 같은 날짜의 관리자 행을 덮어쓰지 않는다.
     * V8의 날짜 단독 PK에서는 {@code save()}가 merge로 동작해 조용히 덮어썼다.
     */
    @Test
    @DisplayName("같은 날짜라도 출처가 다르면 별개 행으로 공존한다")
    void rowsOfDifferentSourcesCoexistOnSameDate() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        holidayRepository.save(Holiday.manualWorkingDay(date, "정상 근무(수동 보정)"));
        flushAndClear();

        holidayRepository.save(Holiday.fromApi(date, "제헌절"));
        flushAndClear();

        assertThat(holidayRepository.count()).isEqualTo(2);
        assertThat(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(date, date))
                .extracting(Holiday::getSource, Holiday::isHoliday)
                .containsExactlyInAnyOrder(
                        tuple(HolidaySource.API, true),
                        tuple(HolidaySource.MANUAL, false)
                );
    }

    @Test
    @DisplayName("같은 (날짜, 출처)를 다시 저장하면 행이 늘지 않고 덮어써진다")
    void saveOverwritesSameDateAndSource() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        holidayRepository.save(Holiday.fromApi(date, "임시공휴일"));
        flushAndClear();

        holidayRepository.save(Holiday.fromApi(date, "제헌절"));
        flushAndClear();

        assertThat(holidayRepository.count()).isEqualTo(1);
        assertThat(holidayRepository.findById(new HolidayId(date, HolidaySource.API)).orElseThrow().getName())
                .isEqualTo("제헌절");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
