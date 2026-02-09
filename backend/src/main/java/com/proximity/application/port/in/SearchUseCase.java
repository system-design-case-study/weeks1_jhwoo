package com.proximity.application.port.in;

import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;

public interface SearchUseCase {

    SearchResponse search(SearchRequest request);
}
