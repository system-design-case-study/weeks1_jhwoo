package com.proximity.application.exception;

public class DuplicateBusinessException extends RuntimeException {

    public DuplicateBusinessException() {
        super("동일한 이름과 위치의 사업장이 이미 등록되어 있습니다");
    }
}
