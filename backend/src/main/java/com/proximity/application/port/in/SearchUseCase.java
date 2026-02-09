package com.proximity.application.port.in;

import com.proximity.application.dto.BoundsSearchRequest;
import com.proximity.application.dto.BoundsSearchResponse;
import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;

public interface SearchUseCase {

    SearchResponse search(SearchRequest request);

    BoundsSearchResponse searchByBounds(BoundsSearchRequest request);
}
