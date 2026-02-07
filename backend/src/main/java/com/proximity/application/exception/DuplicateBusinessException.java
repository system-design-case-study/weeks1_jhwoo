package com.proximity.application.exception;

public class DuplicateBusinessException extends RuntimeException {

    public DuplicateBusinessException() {
        super("동일한 소유주, 이름, 위치의 사업장이 이미 존재합니다");
    }
}
