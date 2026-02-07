package com.proximity.application.dto;

public record BusinessSummary(
        Long id,
        String name,
        String address,
        double distance,
        String category
) {
}
