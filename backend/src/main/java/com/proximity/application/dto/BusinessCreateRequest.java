package com.proximity.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BusinessCreateRequest(

        @NotBlank(message = "사업장 이름은 필수 값입니다")
        String name,

        @NotBlank(message = "주소는 필수 값입니다")
        String address,

        @NotNull(message = "위도는 필수 값입니다")
        @Min(value = -90, message = "위도는 -90 이상이어야 합니다")
        @Max(value = 90, message = "위도는 90 이하여야 합니다")
        Double latitude,

        @NotNull(message = "경도는 필수 값입니다")
        @Min(value = -180, message = "경도는 -180 이상이어야 합니다")
        @Max(value = 180, message = "경도는 180 이하여야 합니다")
        Double longitude,

        String phone,

        String category,

        List<BusinessHoursCreateRequest> businessHours
) {

    public record BusinessHoursCreateRequest(
            @Min(value = 0, message = "요일은 0(월) 이상이어야 합니다")
            @Max(value = 6, message = "요일은 6(일) 이하여야 합니다")
            int dayOfWeek,
            String openTime,
            String closeTime,
            boolean closed
    ) {
    }
}
