package com.proximity.application.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record BusinessDetailResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        String phone,
        String category,
        Long ownerId,
        List<BusinessHoursDto> businessHours,
        List<BusinessPhotoDto> photos,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public record BusinessHoursDto(
            Long id,
            int dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed
    ) {
    }

    public record BusinessPhotoDto(
            Long id,
            String photoUrl,
            int displayOrder
    ) {
    }
}
