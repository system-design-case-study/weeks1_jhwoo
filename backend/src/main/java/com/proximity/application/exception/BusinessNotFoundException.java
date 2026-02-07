package com.proximity.application.exception;

public class BusinessNotFoundException extends RuntimeException {

    public BusinessNotFoundException(Long id) {
        super("사업장을 찾을 수 없습니다: " + id);
    }
}
