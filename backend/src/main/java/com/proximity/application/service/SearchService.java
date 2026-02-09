package com.proximity.application.service;

import com.proximity.application.dto.BoundsSearchRequest;
import com.proximity.application.dto.BoundsSearchResponse;
import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.exception.InvalidRadiusException;
import com.proximity.application.port.in.SearchUseCase;
import com.proximity.application.port.out.CachePort;
import com.proximity.application.port.out.SearchPort;
import com.proximity.config.MetricsConfig.CacheHitRatioHolder;
import io.micrometer.core.instrument.DistributionSummary;
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
    private final DistributionSummary searchResultCountSummary;
    private final DistributionSummary searchRadiusHistogram;
    private final CacheHitRatioHolder cacheHitRatioHolder;

    public SearchService(SearchPort searchPort,
                         CachePort cachePort,
                         DistributionSummary searchResultCountSummary,
                         DistributionSummary searchRadiusHistogram,
                         CacheHitRatioHolder cacheHitRatioHolder) {
        this.searchPort = searchPort;
        this.cachePort = cachePort;
        this.searchResultCountSummary = searchResultCountSummary;
        this.searchRadiusHistogram = searchRadiusHistogram;
        this.cacheHitRatioHolder = cacheHitRatioHolder;
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        validateRadius(request.radius());
        searchRadiusHistogram.record(request.radius());

        String cacheKey = GridCacheKeyGenerator.searchKey(
                request.latitude(), request.longitude(), request.radius(),
                request.page(), request.size());

        Optional<SearchResponse> cached = cachePort.getSearchCache(cacheKey);
        if (cached.isPresent()) {
            cacheHitRatioHolder.recordHit();
            return cached.get();
        }

        cacheHitRatioHolder.recordMiss();

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

        searchResultCountSummary.record(total);

        SearchResponse response = new SearchResponse(businesses, total, request.page(), request.size());
        cachePort.putSearchCache(cacheKey, response);
        return response;
    }

    @Override
    public BoundsSearchResponse searchByBounds(BoundsSearchRequest request) {
        double centerLat = (request.swLat() + request.neLat()) / 2.0;
        double centerLng = (request.swLng() + request.neLng()) / 2.0;

        String cacheKey = GridCacheKeyGenerator.boundsKey(
                request.swLat(), request.swLng(),
                request.neLat(), request.neLng(),
                request.size());

        Optional<SearchResponse> cached = cachePort.getSearchCache(cacheKey);
        if (cached.isPresent()) {
            cacheHitRatioHolder.recordHit();
            SearchResponse sr = cached.get();
            return new BoundsSearchResponse(sr.businesses(), sr.businesses().size());
        }

        cacheHitRatioHolder.recordMiss();

        List<BusinessSummary> businesses = searchPort.searchByBounds(
                request.swLat(), request.swLng(),
                request.neLat(), request.neLng(),
                centerLat, centerLng,
                request.size()
        );

        searchResultCountSummary.record(businesses.size());

        SearchResponse forCache = new SearchResponse(businesses, businesses.size(), 0, request.size());
        cachePort.putSearchCache(cacheKey, forCache);

        return new BoundsSearchResponse(businesses, businesses.size());
    }

    private void validateRadius(double radiusKm) {
        if (!ALLOWED_RADII_KM.contains(radiusKm)) {
            throw new InvalidRadiusException(radiusKm);
        }
    }
}
