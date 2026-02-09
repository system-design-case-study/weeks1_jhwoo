package com.proximity.application.dto;

import java.util.List;

public record BoundsSearchResponse(
        List<BusinessSummary> businesses,
        int count
) {
}
