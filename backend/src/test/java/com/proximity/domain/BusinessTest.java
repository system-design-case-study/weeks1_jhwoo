package com.proximity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessTest {

    @Nested
    @DisplayName("좌표 검증")
    class CoordinateValidation {

        @Test
        @DisplayName("유효한 좌표로 생성 시 성공한다")
        void validCoordinates() {
            // given & when & then
            assertDoesNotThrow(() ->
                    new Business("테스트", "주소", 37.5665, 126.9780, "010-1234-5678", "음식점", 1L)
            );
        }

        @ParameterizedTest
        @DisplayName("위도 범위를 초과하면 실패한다")
        @CsvSource({"-91.0, 126.9780", "91.0, 126.9780", "-90.001, 126.9780", "90.001, 126.9780"})
        void invalidLatitude(double lat, double lng) {
            // given & when & then
            assertThrows(IllegalArgumentException.class, () ->
                    new Business("테스트", "주소", lat, lng, null, null, 1L)
            );
        }

        @ParameterizedTest
        @DisplayName("경도 범위를 초과하면 실패한다")
        @CsvSource({"37.5665, -181.0", "37.5665, 181.0", "37.5665, -180.001", "37.5665, 180.001"})
        void invalidLongitude(double lat, double lng) {
            // given & when & then
            assertThrows(IllegalArgumentException.class, () ->
                    new Business("테스트", "주소", lat, lng, null, null, 1L)
            );
        }

        @ParameterizedTest
        @DisplayName("경계값 좌표로 생성 시 성공한다")
        @CsvSource({"-90.0, -180.0", "90.0, 180.0", "0.0, 0.0", "-90.0, 180.0", "90.0, -180.0"})
        void boundaryCoordinates(double lat, double lng) {
            // given & when & then
            assertDoesNotThrow(() ->
                    new Business("테스트", "주소", lat, lng, null, null, 1L)
            );
        }

        @Test
        @DisplayName("위치 변경 시에도 좌표 검증이 적용된다")
        void updateLocationValidation() {
            // given
            Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);

            // when & then
            assertThrows(IllegalArgumentException.class, () ->
                    business.updateLocation(91.0, 126.9780)
            );
        }
    }

    @Nested
    @DisplayName("소유권 확인")
    class OwnershipCheck {

        @Test
        @DisplayName("소유주 ID가 일치하면 true를 반환한다")
        void ownedByCorrectOwner() {
            // given
            Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);

            // when & then
            assertTrue(business.isOwnedBy(1L));
        }

        @Test
        @DisplayName("소유주 ID가 불일치하면 false를 반환한다")
        void notOwnedByDifferentOwner() {
            // given
            Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);

            // when & then
            assertFalse(business.isOwnedBy(2L));
        }
    }

    @Test
    @DisplayName("생성 시 필드가 올바르게 설정된다")
    void fieldsAreSetCorrectly() {
        // given & when
        Business business = new Business("카페", "서울시 강남구", 37.5665, 126.9780, "02-1234-5678", "카페", 1L);

        // then
        assertEquals("카페", business.getName());
        assertEquals("서울시 강남구", business.getAddress());
        assertEquals(37.5665, business.getLatitude());
        assertEquals(126.9780, business.getLongitude());
        assertEquals("02-1234-5678", business.getPhone());
        assertEquals("카페", business.getCategory());
        assertEquals(1L, business.getOwnerId());
    }
}
