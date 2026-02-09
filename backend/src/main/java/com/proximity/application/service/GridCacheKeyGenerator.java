package com.proximity.application.service;

import java.util.ArrayList;
import java.util.List;

public final class GridCacheKeyGenerator {

    private static final double GRID_SIZE = 0.01;
    private static final String SEARCH_PREFIX = "search:";
    private static final String BUSINESS_PREFIX = "business:";

    private GridCacheKeyGenerator() {
    }

    public static String searchKey(double lat, double lng, double radiusKm) {
        return searchKey(lat, lng, radiusKm, 0, 20);
    }

    public static String searchKey(double lat, double lng, double radiusKm, int page, int size) {
        double roundedLat = roundToGrid(lat);
        double roundedLng = roundToGrid(lng);
        return SEARCH_PREFIX + roundedLat + ":" + roundedLng + ":" + radiusKm + ":" + page + ":" + size;
    }

    public static String businessKey(Long id) {
        return BUSINESS_PREFIX + id;
    }

    public static List<String> adjacentSearchKeyPatterns(double lat, double lng) {
        double centerLat = roundToGrid(lat);
        double centerLng = roundToGrid(lng);

        List<String> patterns = new ArrayList<>(9);
        for (int dLat = -1; dLat <= 1; dLat++) {
            for (int dLng = -1; dLng <= 1; dLng++) {
                double adjLat = roundToGrid(centerLat + dLat * GRID_SIZE);
                double adjLng = roundToGrid(centerLng + dLng * GRID_SIZE);
                patterns.add(SEARCH_PREFIX + adjLat + ":" + adjLng + ":*");
            }
        }
        return patterns;
    }

    static double roundToGrid(double value) {
        return Math.round(value / GRID_SIZE) * GRID_SIZE;
    }
}
