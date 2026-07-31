package com.company.officecommute.repository.holiday;

import com.company.officecommute.domain.holiday.Holiday;
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

@DataJpaTest
class HolidayRepositoryTest {

    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("날짜를 PK로 저장하고 조회한다")
    void savesWithDateAsPrimaryKey() {
        holidayRepository.save(Holiday.fromApi(LocalDate.of(2026, 1, 1), "1월1일"));
        flushAndClear();

        Optional<Holiday> found = holidayRepository.findById(LocalDate.of(2026, 1, 1));

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("1월1일");
        assertThat(found.get().getSource()).isEqualTo(HolidaySource.API);
    }

    @Test
    @DisplayName("수동 등록 행도 출처가 보존된 채 저장된다")
    void savesManualRow() {
        holidayRepository.save(Holiday.manual(LocalDate.of(2026, 6, 3), "제21대 대통령 선거(사후 지정)"));
        flushAndClear();

        Holiday found = holidayRepository.findById(LocalDate.of(2026, 6, 3)).orElseThrow();

        assertThat(found.isManual()).isTrue();
        assertThat(found.getSource()).isEqualTo(HolidaySource.MANUAL);
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
     * PK가 할당식(LocalDate)이라 {@code save()}는 merge로 동작해 같은 날짜를 조용히 덮어쓴다.
     * 동기화가 MANUAL 행을 보존하려면 이 동작에 기대지 말고 기존 행을 먼저 조회해 판단해야 한다
     * ({@code HolidayLedgerService.applyApiSync}가 그렇게 한다).
     */
    @Test
    @DisplayName("같은 날짜를 다시 저장하면 행이 늘지 않고 덮어써진다")
    void saveOverwritesSameDate() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        holidayRepository.save(Holiday.manual(date, "제헌절(수동 보정)"));
        flushAndClear();

        holidayRepository.save(Holiday.fromApi(date, "제헌절"));
        flushAndClear();

        assertThat(holidayRepository.count()).isEqualTo(1);
        assertThat(holidayRepository.findById(date).orElseThrow().getSource())
                .isEqualTo(HolidaySource.API);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
