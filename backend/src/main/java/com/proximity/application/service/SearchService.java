package com.proximity.application.service;

import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.exception.InvalidRadiusException;
import com.proximity.application.port.in.SearchUseCase;
import com.proximity.application.port.out.CachePort;
import com.proximity.application.port.out.SearchPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SearchService implements SearchUseCase {

    private static final Set<Double> ALLOWED_RADII_KM = Set.of(0.5, 1.0, 2.0, 5.0, 20.0);
    private static final double KM_TO_METERS = 1000.0;

    private final SearchPort searchPort;
    private final CachePort cachePort;

    public SearchService(SearchPort searchPort, CachePort cachePort) {
        this.searchPort = searchPort;
        this.cachePort = cachePort;
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        validateRadius(request.radius());

        String cacheKey = GridCacheKeyGenerator.searchKey(
                request.latitude(), request.longitude(), request.radius());

        Optional<SearchResponse> cached = cachePort.getSearchCache(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        double radiusMeters = request.radius() * KM_TO_METERS;

        List<BusinessSummary> businesses = searchPort.searchByLocation(
                request.latitude(),
                request.longitude(),
                radiusMeters,
                request.page(),
                request.size()
        );

        long total = searchPort.countByLocation(
                request.latitude(),
                request.longitude(),
                radiusMeters
        );

        SearchResponse response = new SearchResponse(businesses, total, request.page(), request.size());
        cachePort.putSearchCache(cacheKey, response);
        return response;
    }

    private void validateRadius(double radiusKm) {
        if (!ALLOWED_RADII_KM.contains(radiusKm)) {
            throw new InvalidRadiusException(radiusKm);
        }
    }
}
