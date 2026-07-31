package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.service.overtime.TotalWorkingMinutes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommuteHistoryRepository extends JpaRepository<CommuteHistory, Long> {

    Optional<CommuteHistory> findFirstByEmployeeIdOrderByWorkStartTimeDesc(Long employeeId);

    Optional<CommuteHistory> findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(
            Long employeeId
    );

    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<CommuteHistory> findAllByEmployeeIdAndWorkDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    /**
     * 해당 기간에 퇴근이 찍히지 않은(마감되지 않은) 출근 기록 수.
     * <p>
     * 이런 기록은 {@code workingMinutes = 0}으로 초과근무 SUM에 들어가므로, 해당 직원의 초과근무가
     * 실제보다 적게 집계된다. 리포트가 "0분"과 "아직 안 찍음"을 구분해 보여줄 수 있도록 건수를 노출한다.<br>
     * 연차 기록({@code registerAnnualLeave})은 {@code workEndTime}이 채워지므로 여기 잡히지 않는다.
     */
    long countByWorkDateBetweenAndWorkEndTimeIsNull(LocalDate startDate, LocalDate endDate);

    /**
     * 퇴근 처리. {@code workEndTime IS NULL} 조건으로 상태 확인과 변경을 단일 UPDATE로 묶어,
     * 동시 퇴근 요청 중 정확히 한 건만 성공한다(나머지는 0 반환).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CommuteHistory ch
            SET ch.workEndTime = :workEndTime, ch.workingMinutes = :workingMinutes
            WHERE ch.commuteHistoryId = :commuteHistoryId
                AND ch.workEndTime IS NULL
            """)
    int updateWorkEndTimeIfOpen(Long commuteHistoryId, Instant workEndTime, long workingMinutes);

    /**
     * Aggregates total working minutes per employee within the given work_date range.
     * <p>
     * - Filters on work_date (LocalDate, employee-zone authoritative) instead of workStartTime instants,
     *   so JVM default zone doesn't influence month classification.<br>
     * - Uses LEFT JOIN on team so employees without a team are included.<br>
     * - COALESCE(t.name, "미배정") ensures the team name is never null (unassigned → "미배정").<br>
     * - GROUP BY includes team name to align with the select list and avoid SQL grouping errors.
     */
    @Query("""
            SELECT new com.company.officecommute.service.overtime.TotalWorkingMinutes(
                        ch.employeeId, e.employeeCode, e.name, COALESCE(t.name, '미배정') , SUM(ch.workingMinutes)
                    )
            FROM CommuteHistory ch
            JOIN Employee e ON ch.employeeId = e.employeeId
            LEFT JOIN e.team t
            WHERE ch.workDate BETWEEN :startDate AND :endDate
            GROUP BY ch.employeeId, e.employeeCode, e.name, t.name
            """)
    List<TotalWorkingMinutes> findTotalWorkingMinutesByWorkDateBetween(LocalDate startDate, LocalDate endDate);
}
