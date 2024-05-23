package com.company.officecommute.domain.employee;

import com.company.officecommute.service.employee.EmployeeBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class EmployeeTest {

    @ParameterizedTest
    @NullAndEmptySource
    void testEmployeeNameException(String input) {
        Assertions.assertThatThrownBy(() -> new EmployeeBuilder().withId(1L)
                        .withName(input)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("employee의 name(%s)이 올바르지 않은 형식입니다. 다시 입력해주세요.", input));
    }

    @Test
    void testEmployeeRoleException() {
        Assertions.assertThatThrownBy(() -> new EmployeeBuilder().withId(1L)
                        .withName("input")
                        .withRole(null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("employee의 role이 올바르지 않은 형식(%s)입니다. 다시 입력해주세요.", null));
    }

    @Test
    void testEmployeeBirthdayException() {
        Assertions.assertThatThrownBy(() -> new EmployeeBuilder().withId(1L)
                        .withName("input")
                        .withRole(Role.MANAGER)
                        .withBirthday(null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("employee의 birthday이 올바르지 않은 형식(%s)입니다. 다시 입력해주세요.", null));
    }
}
