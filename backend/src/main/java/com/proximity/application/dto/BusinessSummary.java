package com.proximity.application.dto;

public record BusinessSummary(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        double distance,
        String category
) {
}
