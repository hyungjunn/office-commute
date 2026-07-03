package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationFailedException("로그인이 필요합니다.");
        }

        Long employeeId = (Long) session.getAttribute("currentEmployeeId");
        Role role = (Role) session.getAttribute("currentRole");

        if (employeeId == null || role == null) {
            throw new AuthenticationFailedException("로그인이 필요합니다.");
        }

        request.setAttribute("currentEmployeeId", employeeId);
        request.setAttribute("currentRole", role);

        if (handler instanceof HandlerMethod handlerMethod) {
            ManagerOnly managerOnly = handlerMethod.getMethodAnnotation(ManagerOnly.class);
            if (managerOnly != null && role != Role.MANAGER) {
                throw new ForbiddenException("접근 권한이 없습니다.");
            }
        }

        return true;
    }
}
