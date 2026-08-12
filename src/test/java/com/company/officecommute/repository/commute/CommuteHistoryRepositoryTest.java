package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.overtime.DailyWorkingMinutes;
import com.company.officecommute.service.overtime.UnclosedCommute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CommuteHistoryRepositoryTest {

    @Autowired
    private CommuteHistoryRepository commuteHistoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("findDailyWorkingMinutesByWorkDateBetween — 직원·일자별로 한 행씩 근무 분을 반환한다")
    void findDailyWorkingMinutesByWorkDateBetween_returnsOneRowPerEmployeeDay() {
        // given — 초과근무는 일 8h·주 40h 기준 계산이라 SUM이 아니라 일별 행이 필요하다
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        commuteHistoryRepository.saveAll(List.of(
                CommuteHistoryFixture.ended(null, 1L,
                        ZonedDateTime.of(2024, 8, 5, 9, 0, 0, 0, zoneId),
                        ZonedDateTime.of(2024, 8, 5, 18, 0, 0, 0, zoneId)),
                CommuteHistoryFixture.ended(null, 1L,
                        ZonedDateTime.of(2024, 8, 6, 9, 0, 0, 0, zoneId),
                        ZonedDateTime.of(2024, 8, 6, 17, 0, 0, 0, zoneId)),
                CommuteHistoryFixture.ended(null, 2L,
                        ZonedDateTime.of(2024, 8, 7, 9, 0, 0, 0, zoneId),
                        ZonedDateTime.of(2024, 8, 7, 19, 0, 0, 0, zoneId))
        ));

        // when
        List<DailyWorkingMinutes> result = commuteHistoryRepository.findDailyWorkingMinutesByWorkDateBetween(
                LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then — 합산 없이 날짜별로 그대로 남는다
        assertThat(result).containsExactlyInAnyOrder(
                new DailyWorkingMinutes(1L, LocalDate.of(2024, 8, 5), 540L),
                new DailyWorkingMinutes(1L, LocalDate.of(2024, 8, 6), 480L),
                new DailyWorkingMinutes(2L, LocalDate.of(2024, 8, 7), 600L)
        );
    }

    @Test
    @DisplayName("findAllByEmployeeIdAndWorkDateBetween — 1일과 말일은 포함, 전월 말일과 다음월 1일은 제외")
    void findAllByEmployeeIdAndWorkDateBetween_respectsMonthBoundaries() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Long employeeId = 42L;
        // 7/31(전월 말일), 8/1(당월 1일), 8/31(당월 말일), 9/1(다음월 1일)
        commuteHistoryRepository.saveAll(List.of(
                CommuteHistoryFixture.ended(null, employeeId,
                        ZonedDateTime.of(2024, 7, 31, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 7, 31, 18, 0, 0, 0, zone), zone),
                CommuteHistoryFixture.ended(null, employeeId,
                        ZonedDateTime.of(2024, 8, 1, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 8, 1, 18, 0, 0, 0, zone), zone),
                CommuteHistoryFixture.ended(null, employeeId,
                        ZonedDateTime.of(2024, 8, 31, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 8, 31, 18, 0, 0, 0, zone), zone),
                CommuteHistoryFixture.ended(null, employeeId,
                        ZonedDateTime.of(2024, 9, 1, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 9, 1, 18, 0, 0, 0, zone), zone)
        ));

        // when
        List<CommuteHistory> august = commuteHistoryRepository.findAllByEmployeeIdAndWorkDateBetween(
                employeeId, LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then
        assertThat(august)
                .extracting(CommuteHistory::getWorkDate)
                .containsExactlyInAnyOrder(LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));
    }

    @Test
    @DisplayName("findDailyWorkingMinutesByWorkDateBetween — 연차·퇴근 미마감 기록은 0분 행으로 나타난다")
    void findDailyWorkingMinutesByWorkDateBetween_returnsZeroMinuteRowsForLeaveAndOpenCommute() {
        // given — 연차는 실근로 0으로 주 40h 산정에 포함되지 않아야 하고(기준선은 40h 유지),
        // 미마감 기록은 0분으로 잡혀 과소 집계 신호와 짝을 이룬다.
        ZoneId zone = ZoneId.of("Asia/Seoul");
        commuteHistoryRepository.saveAll(List.of(
                CommuteHistoryFixture.annualLeave(1L, LocalDate.of(2024, 8, 1), zone),
                CommuteHistoryFixture.open(null, 2L, ZonedDateTime.of(2024, 8, 2, 9, 0, 0, 0, zone), zone)
        ));

        // when
        List<DailyWorkingMinutes> result = commuteHistoryRepository.findDailyWorkingMinutesByWorkDateBetween(
                LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then
        assertThat(result).containsExactlyInAnyOrder(
                new DailyWorkingMinutes(1L, LocalDate.of(2024, 8, 1), 0L),
                new DailyWorkingMinutes(2L, LocalDate.of(2024, 8, 2), 0L)
        );
    }

    @Test
    @DisplayName("findUnclosedByWorkDateBetween — 미마감만 근무일순으로 반환하고 연차·마감·범위 밖은 제외한다")
    void findUnclosedByWorkDateBetween_returnsOnlyOpenCommutesInRange() {
        // given — 이 목록은 대표 발송을 막는 게이트이자 관리자 교정 요청의 본문이다.
        // 연차는 workEndTime이 채워지므로 미마감이 아니고, 범위 밖 기록은 다음 달 리포트 몫이다.
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Long open1 = persistEmployee("EMP001", "임형준");
        Long open2 = persistEmployee("EMP002", "김사원");
        Long closed = persistEmployee("EMP003", "박대리");
        Long onLeave = persistEmployee("EMP004", "최과장");
        Long nextMonth = persistEmployee("EMP005", "정부장");
        commuteHistoryRepository.saveAll(List.of(
                CommuteHistoryFixture.open(null, open1, ZonedDateTime.of(2024, 8, 31, 9, 0, 0, 0, zone), zone),
                CommuteHistoryFixture.open(null, open2, ZonedDateTime.of(2024, 8, 30, 9, 0, 0, 0, zone), zone),
                CommuteHistoryFixture.ended(null, closed,
                        ZonedDateTime.of(2024, 8, 5, 9, 0, 0, 0, zone),
                        ZonedDateTime.of(2024, 8, 5, 18, 0, 0, 0, zone)),
                CommuteHistoryFixture.annualLeave(onLeave, LocalDate.of(2024, 8, 6), zone),
                CommuteHistoryFixture.open(null, nextMonth, ZonedDateTime.of(2024, 9, 1, 9, 0, 0, 0, zone), zone)
        ));

        // when
        List<UnclosedCommute> unclosed = commuteHistoryRepository.findUnclosedByWorkDateBetween(
                LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then — 사번·이름 프로젝션과 근무일 오름차순 정렬까지 SQL 레벨에서 고정한다
        assertThat(unclosed).containsExactly(
                new UnclosedCommute("EMP002", "김사원", LocalDate.of(2024, 8, 30)),
                new UnclosedCommute("EMP001", "임형준", LocalDate.of(2024, 8, 31))
        );
    }

    @Test
    @DisplayName("findUnclosedByWorkDateBetween — 직원 행이 없는 미마감 기록도 게이트에서 사라지지 않는다")
    void findUnclosedByWorkDateBetween_keepsOrphanRows() {
        // given — 직원 행이 사라진(잘못된 데이터) 미마감 기록이 조인에서 탈락하면
        // 게이트가 조용히 좁아져, 데이터가 깨졌을수록 리포트가 그냥 나가는 역전이 생긴다.
        ZoneId zone = ZoneId.of("Asia/Seoul");
        commuteHistoryRepository.save(
                CommuteHistoryFixture.open(null, 999L, ZonedDateTime.of(2024, 8, 30, 9, 0, 0, 0, zone), zone));

        // when
        List<UnclosedCommute> unclosed = commuteHistoryRepository.findUnclosedByWorkDateBetween(
                LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));

        // then
        assertThat(unclosed).containsExactly(
                new UnclosedCommute("(직원 정보 없음)", "(직원 정보 없음)", LocalDate.of(2024, 8, 30)));
    }

    private Long persistEmployee(String employeeCode, String name) {
        Employee employee = new EmployeeBuilder()
                .withName(name)
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2020, 1, 1))
                .withEmployeeCode(employeeCode)
                .withEmail(employeeCode.toLowerCase() + "@company.com")
                .withPassword("password")
                .build();
        return entityManager.persistAndFlush(employee).getEmployeeId();
    }

    @Test
    @DisplayName("existsByEmployeeIdAndWorkDate — 동일 일자 기록이 있으면 true")
    void existsByEmployeeIdAndWorkDate_returnsTrue_whenRecordExists() {
        // given
        Long employeeId = 1L;
        ZonedDateTime start = ZonedDateTime.of(2026, 5, 23, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        commuteHistoryRepository.save(CommuteHistoryFixture.open(null, employeeId, start, ZoneId.of("Asia/Seoul")));

        // when
        boolean exists = commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, LocalDate.of(2026, 5, 23));

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("updateWorkEndTimeIfOpen — 미종료 근무면 1건 update되고 퇴근 시각과 근무 분이 반영된다")
    void updateWorkEndTimeIfOpen_updatesOpenCommute() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone);
        CommuteHistory open = commuteHistoryRepository.save(
                CommuteHistoryFixture.open(null, 1L, start, zone));

        // when
        int updated = commuteHistoryRepository.updateWorkEndTimeIfOpen(
                open.getCommuteHistoryId(), start.plusHours(9).toInstant(), 540);

        // then
        assertThat(updated).isEqualTo(1);
        CommuteHistory found = commuteHistoryRepository.findById(open.getCommuteHistoryId()).orElseThrow();
        assertThat(found.getWorkEndTime()).isEqualTo(start.plusHours(9).toInstant());
        assertThat(found.getWorkingMinutes()).isEqualTo(540);
    }

    @Test
    @DisplayName("updateWorkEndTimeIfOpen — 이미 종료된 근무면 0건이고 기존 값이 유지된다")
    void updateWorkEndTimeIfOpen_returnsZero_whenAlreadyEnded() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone);
        ZonedDateTime firstEnd = start.plusHours(9);
        CommuteHistory ended = commuteHistoryRepository.save(
                CommuteHistoryFixture.ended(null, 1L, start, firstEnd, zone));

        // when
        int updated = commuteHistoryRepository.updateWorkEndTimeIfOpen(
                ended.getCommuteHistoryId(), start.plusHours(10).toInstant(), 600);

        // then
        assertThat(updated).isZero();
        CommuteHistory found = commuteHistoryRepository.findById(ended.getCommuteHistoryId()).orElseThrow();
        assertThat(found.getWorkEndTime()).isEqualTo(firstEnd.toInstant());
        assertThat(found.getWorkingMinutes()).isEqualTo(540);
    }

    @Test
    @DisplayName("같은 직원이 같은 work_date로 두 번째 저장하면 uk_commute_history_employee_date 위반이 발생한다")
    void savingSecondHistoryOnSameEmployeeAndDateViolatesUniqueConstraint() {
        // given — 같은 날, 다른 시각: 제약이 timestamp가 아니라 work_date에 걸려 있음을 함께 검증한다.
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Long employeeId = 1L;
        commuteHistoryRepository.save(CommuteHistoryFixture.ended(null, employeeId,
                ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone),
                ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, zone), zone));

        CommuteHistory sameDayAgain = CommuteHistoryFixture.open(null, employeeId,
                ZonedDateTime.of(2026, 6, 1, 14, 0, 0, 0, zone), zone);

        // when & then
        assertThatThrownBy(() -> commuteHistoryRepository.saveAndFlush(sameDayAgain))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_COMMUTE_HISTORY_EMPLOYEE_DATE");
    }

    @Test
    @DisplayName("다른 직원은 같은 work_date에 저장할 수 있다 — 제약은 (employee_id, work_date) 복합키다")
    void differentEmployeesMayShareSameWorkDate() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone);
        commuteHistoryRepository.save(CommuteHistoryFixture.open(null, 1L, start, zone));

        // when
        commuteHistoryRepository.saveAndFlush(CommuteHistoryFixture.open(null, 2L, start, zone));

        // then
        assertThat(commuteHistoryRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("existsByEmployeeIdAndWorkDate — 다른 일자만 있으면 false")
    void existsByEmployeeIdAndWorkDate_returnsFalse_whenNoRecordOnThatDate() {
        // given
        Long employeeId = 1L;
        ZonedDateTime start = ZonedDateTime.of(2026, 5, 22, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        commuteHistoryRepository.save(CommuteHistoryFixture.open(null, employeeId, start, ZoneId.of("Asia/Seoul")));

        // when
        boolean exists = commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, LocalDate.of(2026, 5, 23));

        // then
        assertThat(exists).isFalse();
    }
}
