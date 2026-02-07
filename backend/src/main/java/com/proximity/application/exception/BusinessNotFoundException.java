package com.proximity.application.exception;

public class BusinessNotFoundException extends RuntimeException {

    public BusinessNotFoundException(Long id) {
        super("존재하지 않거나 삭제된 사업장입니다");
    }
}
