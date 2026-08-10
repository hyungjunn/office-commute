package com.company.officecommute.domain.employee;

import com.company.officecommute.domain.team.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeTest {

    @ParameterizedTest
    @NullAndEmptySource
    void testEmployeeNameException(String input) {
        assertThatThrownBy(() -> new EmployeeBuilder()
                        .withId(1L)
                        .withName(input)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("employee의 name이 올바르지 않은 형식입니다.");
    }

    @Test
    void testChangeTeam() {
        Employee employee = new EmployeeBuilder()
                .withId(1L)
                .withName("hyungjunn")
                .withRole(Role.MANAGER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2021, 8, 18))
                .withEmployeeCode("EMP001")
                .withEmail("hyungjunn@company.com")
                .withPassword("password123")
                .build();

        employee.changeTeam(Team.register("A", null, 0));
        assertThat(employee.getTeamName()).isEqualTo("A");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void registerDefaultsTimezoneWhenBlank(String timezone) {
        Employee employee = Employee.register(
                "hyungjunn",
                Role.MANAGER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2021, 8, 18),
                "EMP001",
                "hyungjunn@company.com",
                "password123",
                timezone,
                null
        );

        assertThat(employee.getTimezone()).isEqualTo(Employee.DEFAULT_TIMEZONE);
    }

    @Nested
    @DisplayName("changeWorkEndDate는")
    class ChangeWorkEndDate {

        private final LocalDate workStartDate = LocalDate.of(2021, 8, 18);

        private Employee employee() {
            return new EmployeeBuilder()
                    .withId(1L)
                    .withName("hyungjunn")
                    .withRole(Role.MANAGER)
                    .withBirthday(LocalDate.of(1998, 8, 18))
                    .withStartDate(workStartDate)
                    .withEmployeeCode("EMP001")
                    .withEmail("hyungjunn@company.com")
                    .withPassword("password123")
                    .build();
        }

        @Test
        @DisplayName("퇴사일을 설정한다")
        void retiresWithValidDate() {
            Employee employee = employee();

            employee.changeWorkEndDate(LocalDate.of(2026, 7, 31));

            assertThat(employee.getWorkEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("입사일 당일 퇴사를 허용한다 — 같은 날 재직은 성립한다")
        void sameDayAsWorkStartDateIsAllowed() {
            Employee employee = employee();

            employee.changeWorkEndDate(workStartDate);

            assertThat(employee.getWorkEndDate()).isEqualTo(workStartDate);
        }

        @Test
        @DisplayName("입사일 이전 날짜를 거부한다")
        void dateBeforeWorkStartDateThrows() {
            Employee employee = employee();

            assertThatThrownBy(() -> employee.changeWorkEndDate(workStartDate.minusDays(1)))
                    .isInstanceOf(InvalidRetirementDateException.class)
                    .hasMessageContaining("퇴사일")
                    .hasMessageContaining("입사일");
        }

        @Test
        @DisplayName("null이면 퇴사를 취소한다")
        void nullClearsRetirement() {
            Employee employee = employee();
            employee.changeWorkEndDate(LocalDate.of(2026, 7, 31));

            employee.changeWorkEndDate(null);

            assertThat(employee.getWorkEndDate()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Invalid/Timezone", "Asia/Invalid"})
    void registerRejectsInvalidTimezone(String timezone) {
        assertThatThrownBy(() -> Employee.register(
                "hyungjunn",
                Role.MANAGER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2021, 8, 18),
                "EMP001",
                "hyungjunn@company.com",
                "password123",
                timezone,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timezone이 올바른 ZoneId 형식이 아닙니다: " + timezone);
    }
}
