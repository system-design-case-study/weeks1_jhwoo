package com.proximity.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SearchRequest(

        @NotNull(message = "위도는 필수 값입니다")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다")
        Double latitude,

        @NotNull(message = "경도는 필수 값입니다")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다")
        Double longitude,

        @NotNull(message = "검색 반경은 필수 값입니다")
        Double radius,

        String category,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
        @Max(value = 200, message = "페이지 크기는 200 이하여야 합니다")
        Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 200;
    private static final int MAX_SIZE = 200;

    public SearchRequest {
        if (page == null) page = DEFAULT_PAGE;
        if (size == null) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        if (category != null && category.isBlank()) category = null;
    }
}
