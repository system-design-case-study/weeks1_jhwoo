package com.proximity.application.port.out;

import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.SearchResponse;

import java.util.Optional;

public interface CachePort {

    Optional<SearchResponse> getSearchCache(String cacheKey);

    void putSearchCache(String cacheKey, SearchResponse response);

    Optional<BusinessDetailResponse> getBusinessCache(Long id);

    void putBusinessCache(Long id, BusinessDetailResponse response);

    void invalidateSearchCache(double lat, double lng);

    void invalidateBusinessCache(Long id);
}
