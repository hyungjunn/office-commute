package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;
import jakarta.servlet.http.HttpServletRequest;

public class AuthUtils {

    public static void requireManagerRole(HttpServletRequest request) {
        Role currentRole = (Role) request.getAttribute("currentRole");
        if (currentRole != Role.MANAGER) {
            throw new IllegalArgumentException("관리자만 접근이 가능합니다");
        }
    }
}
