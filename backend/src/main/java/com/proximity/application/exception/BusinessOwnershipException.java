package com.proximity.application.exception;

public class BusinessOwnershipException extends RuntimeException {

    public BusinessOwnershipException() {
        super("해당 사업장의 소유주가 아닙니다");
    }
}
