package com.proximity.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BoundsSearchRequest(

        @NotNull(message = "남서 위도는 필수 값입니다")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다")
        Double swLat,

        @NotNull(message = "남서 경도는 필수 값입니다")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다")
        Double swLng,

        @NotNull(message = "북동 위도는 필수 값입니다")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다")
        Double neLat,

        @NotNull(message = "북동 경도는 필수 값입니다")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다")
        Double neLng,

        @Min(value = 1, message = "결과 수는 1 이상이어야 합니다")
        @Max(value = 200, message = "결과 수는 200 이하여야 합니다")
        Integer size
) {
    private static final int DEFAULT_SIZE = 200;

    public BoundsSearchRequest {
        if (size == null) size = DEFAULT_SIZE;
    }
}
