package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("employeeId") == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        Long employeeId = (Long) session.getAttribute("employeeId");
        Role employeeRole = (Role) session.getAttribute("employeeRole");
        request.setAttribute("currentEmployeeId", employeeId);
        request.setAttribute("currentRole", employeeRole);
        return true;
    }
}
