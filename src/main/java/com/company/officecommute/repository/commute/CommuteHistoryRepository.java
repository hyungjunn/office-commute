package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.service.overtime.DailyWorkingMinutes;
import com.company.officecommute.service.overtime.UnclosedCommute;
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
     * 이런 기록은 일별 {@code workingMinutes = 0} 행으로 나타나 주 40시간 산정 기반을 깎으므로,
     * 해당 직원의 초과근무가 실제보다 적게 집계된다. 리포트가 "0분"과 "아직 안 찍음"을 구분해
     * 보여줄 수 있도록 건수를 노출한다.<br>
     * 연차 기록({@code registerAnnualLeave})은 {@code workEndTime}이 채워지므로 여기 잡히지 않는다.
     */
    long countByWorkDateBetweenAndWorkEndTimeIsNull(LocalDate startDate, LocalDate endDate);

    /**
     * 위 건수와 <b>같은 범위</b>의 미마감 기록 목록. 관리자에게 "무엇을 마감해야 하는지"를
     * 알리려면 건수만으로는 부족하다.
     * <p>
     * 범위가 {@link #countByWorkDateBetweenAndWorkEndTimeIsNull}와 어긋나면
     * "건수는 3건인데 목록은 2건" 같은 모순이 나오므로, 두 호출의 인자는 한 곳
     * ({@code OverTimeService})에서만 만든다.
     */
    @Query("""
            SELECT new com.company.officecommute.service.overtime.UnclosedCommute(
                        e.employeeCode, e.name, ch.workDate
                    )
            FROM CommuteHistory ch, Employee e
            WHERE e.employeeId = ch.employeeId
                AND ch.workDate BETWEEN :startDate AND :endDate
                AND ch.workEndTime IS NULL
            ORDER BY ch.workDate, e.employeeCode
            """)
    List<UnclosedCommute> findUnclosedByWorkDateBetween(LocalDate startDate, LocalDate endDate);

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
     * 기간 내 직원별·일별 근무 분.
     * <p>
     * 초과근무는 1일 8시간·1주 40시간 기준으로 계산하므로 월 SUM이 아니라 일별 행이 필요하다.
     * work_date(직원 타임존 기준 확정값) 필터라 JVM 기본 타임존이 월 분류에 영향을 주지 않는다.
     * 연차·퇴근 미마감 기록은 workingMinutes = 0 행으로 나타난다.
     */
    @Query("""
            SELECT new com.company.officecommute.service.overtime.DailyWorkingMinutes(
                        ch.employeeId, ch.workDate, ch.workingMinutes
                    )
            FROM CommuteHistory ch
            WHERE ch.workDate BETWEEN :startDate AND :endDate
            """)
    List<DailyWorkingMinutes> findDailyWorkingMinutesByWorkDateBetween(LocalDate startDate, LocalDate endDate);
}
