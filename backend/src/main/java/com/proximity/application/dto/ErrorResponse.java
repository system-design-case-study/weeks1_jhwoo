package com.proximity.application.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
