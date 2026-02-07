package com.proximity.application.exception;

import java.util.Set;

public class InvalidRadiusException extends RuntimeException {

    private static final Set<Double> ALLOWED_RADII_KM = Set.of(0.5, 1.0, 2.0, 5.0, 20.0);

    public InvalidRadiusException(double radius) {
        super("유효하지 않은 검색 반경입니다: " + radius + "km. 허용 값: " + ALLOWED_RADII_KM + " km");
    }
}
