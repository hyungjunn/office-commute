package com.company.officecommute.auth;

public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("로그인이 필요합니다.");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
