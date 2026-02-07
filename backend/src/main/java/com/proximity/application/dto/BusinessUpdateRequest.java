package com.proximity.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.List;

public record BusinessUpdateRequest(

        String name,

        String address,

        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다")
        Double latitude,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다")
        Double longitude,

        String phone,

        String category,

        List<BusinessCreateRequest.BusinessHoursCreateRequest> businessHours
) {
}
