package com.proximity.application.exception;

public class BusinessOwnershipException extends RuntimeException {

    public BusinessOwnershipException() {
        super("해당 사업장에 대한 권한이 없습니다");
    }
}
