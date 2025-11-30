package com.company.officecommute.repository.overtime;

import com.company.officecommute.domain.overtime.Holiday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class HolidayRepositoryTest {

    @Autowired
    private HolidayRepository holidayRepository;

    @Test
    @DisplayName("특정 년월의 공휴일을 저장하고 조회할 수 있다")
    void saveAndFindHolidaysByYearAndMonth() {
        // given
        int year = 2025;
        int month = 11;

        Holiday holiday1 = new Holiday(year, month, LocalDate.of(2025, 11, 3));
        Holiday holiday2 = new Holiday(year, month, LocalDate.of(2025, 11, 15));
        Holiday holiday3 = new Holiday(year, month, LocalDate.of(2025, 11, 28));

        holidayRepository.saveAll(List.of(holiday1, holiday2, holiday3));

        // when
        List<LocalDate> holidays = holidayRepository.findHolidayDatesByYearAndMonth(year, month);

        // then
        assertThat(holidays).hasSize(3);
        assertThat(holidays).containsExactlyInAnyOrder(
                LocalDate.of(2025, 11, 3),
                LocalDate.of(2025, 11, 15),
                LocalDate.of(2025, 11, 28)
        );
    }

    @Test
    @DisplayName("공휴일이 없는 년월을 조회하면 빈 리스트를 반환한다")
    void findHolidaysByYearAndMonth_returnsEmptyList_whenNoHolidays() {
        // when
        List<LocalDate> holidays = holidayRepository.findHolidayDatesByYearAndMonth(2026, 1);

        // then
        assertThat(holidays).isEmpty();
    }

    @Test
    @DisplayName("특정 년월의 공휴일을 삭제할 수 있다")
    void deleteByYearAndMonth() {
        // given
        int year = 2025;
        int month = 12;

        Holiday holiday1 = new Holiday(year, month, LocalDate.of(2025, 12, 25));
        Holiday holiday2 = new Holiday(year, month, LocalDate.of(2025, 12, 31));
        Holiday otherMonthHoliday = new Holiday(2025, 11, LocalDate.of(2025, 11, 15));

        holidayRepository.saveAll(List.of(holiday1, holiday2, otherMonthHoliday));

        // when
        holidayRepository.deleteByYearAndMonth(year, month);

        // then
        List<LocalDate> deletedMonthHolidays = holidayRepository.findHolidayDatesByYearAndMonth(year, month);
        assertThat(deletedMonthHolidays).isEmpty();

        // 다른 월의 데이터는 그대로 유지
        List<LocalDate> otherMonthHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 11);
        assertThat(otherMonthHolidays).hasSize(1);
        assertThat(otherMonthHolidays).contains(LocalDate.of(2025, 11, 15));
    }

    @Test
    @DisplayName("같은 날짜를 여러 번 저장해도 중복 저장된다")
    void saveDuplicateDates() {
        // given
        int year = 2025;
        int month = 5;
        LocalDate date = LocalDate.of(2025, 5, 5);

        Holiday holiday1 = new Holiday(year, month, date);
        Holiday holiday2 = new Holiday(year, month, date);

        holidayRepository.saveAll(List.of(holiday1, holiday2));

        // when
        List<LocalDate> holidays = holidayRepository.findHolidayDatesByYearAndMonth(year, month);

        // then
        assertThat(holidays).hasSize(2);
        assertThat(holidays).containsOnly(date);
    }

    @Test
    @DisplayName("여러 년도의 같은 월 공휴일을 구분해서 조회할 수 있다")
    void findHolidaysByYearAndMonth_distinguishesYears() {
        // given
        Holiday holiday2025 = new Holiday(2025, 1, LocalDate.of(2025, 1, 1));
        Holiday holiday2026 = new Holiday(2026, 1, LocalDate.of(2026, 1, 1));

        holidayRepository.saveAll(List.of(holiday2025, holiday2026));

        // when
        List<LocalDate> holidays2025 = holidayRepository.findHolidayDatesByYearAndMonth(2025, 1);
        List<LocalDate> holidays2026 = holidayRepository.findHolidayDatesByYearAndMonth(2026, 1);

        // then
        assertThat(holidays2025).hasSize(1);
        assertThat(holidays2025).contains(LocalDate.of(2025, 1, 1));

        assertThat(holidays2026).hasSize(1);
        assertThat(holidays2026).contains(LocalDate.of(2026, 1, 1));
    }
}
