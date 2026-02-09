package com.proximity.application.port.out;

import com.proximity.application.dto.BusinessSummary;

import java.util.List;

public interface SearchPort {

    List<BusinessSummary> searchByLocation(double lat, double lng, double radiusMeters, int page, int size);

    long countByLocation(double lat, double lng, double radiusMeters);

    List<BusinessSummary> searchByBounds(double swLat, double swLng,
                                          double neLat, double neLng,
                                          double centerLat, double centerLng,
                                          int size);
}
