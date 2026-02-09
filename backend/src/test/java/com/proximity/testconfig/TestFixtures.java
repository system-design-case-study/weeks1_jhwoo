package com.proximity.testconfig;

import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessCreateRequest.BusinessHoursCreateRequest;
import com.proximity.domain.Business;
import com.proximity.domain.BusinessHours;
import com.proximity.domain.Owner;

import java.time.LocalTime;
import java.util.List;

public final class TestFixtures {

    public static final double DEFAULT_LAT = 37.4979;
    public static final double DEFAULT_LNG = 127.0276;
    public static final Long DEFAULT_OWNER_ID = 1L;

    private TestFixtures() {
    }

    public static Business createBusiness(String name, double lat, double lng, Long ownerId) {
        return new Business(name, "서울시 강남구 테스트로 1", lat, lng, "02-1234-5678", "카페", ownerId);
    }

    public static Business createDefaultBusiness() {
        return createBusiness("테스트 카페", DEFAULT_LAT, DEFAULT_LNG, DEFAULT_OWNER_ID);
    }

    public static Owner createOwner(String email, String passwordHash) {
        return new Owner(email, passwordHash, "테스트 사용자");
    }

    public static Owner createDefaultOwner() {
        return createOwner("test@example.com", "$2a$10$dummyHashForTesting");
    }

    public static BusinessCreateRequest createBusinessRequest(String name, double lat, double lng) {
        return new BusinessCreateRequest(name, "서울시 강남구 테스트로 1", lat, lng,
                "02-1234-5678", "카페", null);
    }

    public static BusinessCreateRequest createBusinessRequestWithHours(String name, double lat, double lng) {
        List<BusinessHoursCreateRequest> hours = List.of(
                new BusinessHoursCreateRequest(0, "09:00", "18:00", false),
                new BusinessHoursCreateRequest(1, "09:00", "18:00", false),
                new BusinessHoursCreateRequest(6, null, null, true)
        );
        return new BusinessCreateRequest(name, "서울시 강남구 테스트로 1", lat, lng,
                "02-1234-5678", "카페", hours);
    }

    public static List<BusinessHours> createBusinessHours() {
        return List.of(
                new BusinessHours(null, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), false),
                new BusinessHours(null, 1, LocalTime.of(9, 0), LocalTime.of(18, 0), false),
                new BusinessHours(null, 6, null, null, true)
        );
    }
}
