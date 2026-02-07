package com.proximity.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record BusinessUpdateRequest(

        String name,

        String address,

        @Min(value = -90, message = "위도는 -90 이상이어야 합니다")
        @Max(value = 90, message = "위도는 90 이하여야 합니다")
        Double latitude,

        @Min(value = -180, message = "경도는 -180 이상이어야 합니다")
        @Max(value = 180, message = "경도는 180 이하여야 합니다")
        Double longitude,

        String phone,

        String category,

        List<BusinessCreateRequest.BusinessHoursCreateRequest> businessHours
) {
}
