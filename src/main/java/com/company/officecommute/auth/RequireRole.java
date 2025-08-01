package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 레벨에서 역할 기반 접근 제어를 위한 애노테이션
 * <p>
 * 사용 예시:
 * <p>
 * - @RequireRole({Role.MANAGER}) // 매니저만 접근 가능
 * - @RequireRole({Role.MANAGER, Role.MEMBER}) // 둘 다 접근 가능
 * - @RequireRole(value = {}, requireLogin = false) // 로그인 불필요
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 접근 허용할 역할들
     * 빈 배열이면 로그인만 체크
     */
    Role[] value() default {};

    /**
     * 접근 허용할 역할들
     * 빈 배열이면 로그인만 체크
     */
    boolean requireLogin() default true;
}
