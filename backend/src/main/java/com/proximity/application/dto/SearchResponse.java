package com.proximity.application.dto;

import java.util.List;

public record SearchResponse(
        List<BusinessSummary> businesses,
        long total,
        int page,
        int size
) {
}
