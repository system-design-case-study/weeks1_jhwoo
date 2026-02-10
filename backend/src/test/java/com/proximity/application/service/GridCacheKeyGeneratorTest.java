package com.proximity.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GridCacheKeyGeneratorTest {

    @Nested
    @DisplayName("searchKey 생성")
    class SearchKey {

        @Test
        @DisplayName("좌표를 0.01도 단위로 반올림하여 캐시 키를 생성한다")
        void generatesRoundedKey() {
            // given
            double lat = 37.5665;
            double lng = 126.9780;
            double radius = 1.0;

            // when
            String key = GridCacheKeyGenerator.searchKey(lat, lng, radius);

            // then
            assertThat(key).isEqualTo("search:37.57:126.98:1.0:ALL:0:200");
        }

        @Test
        @DisplayName("동일 Grid 내 다른 좌표는 동일 캐시 키를 생성한다")
        void sameGridProducesSameKey() {
            // given
            double radius = 1.0;

            // when
            String key1 = GridCacheKeyGenerator.searchKey(37.5665, 126.9780, radius);
            String key2 = GridCacheKeyGenerator.searchKey(37.5671, 126.9783, radius);

            // then
            assertThat(key1).isEqualTo(key2);
        }

        @Test
        @DisplayName("다른 Grid의 좌표는 다른 캐시 키를 생성한다")
        void differentGridProducesDifferentKey() {
            // given
            double radius = 1.0;

            // when
            String key1 = GridCacheKeyGenerator.searchKey(37.5665, 126.9780, radius);
            String key2 = GridCacheKeyGenerator.searchKey(37.5800, 126.9900, radius);

            // then
            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        @DisplayName("다른 반경은 다른 캐시 키를 생성한다")
        void differentRadiusProducesDifferentKey() {
            // given
            double lat = 37.5665;
            double lng = 126.9780;

            // when
            String key1 = GridCacheKeyGenerator.searchKey(lat, lng, 1.0);
            String key2 = GridCacheKeyGenerator.searchKey(lat, lng, 5.0);

            // then
            assertThat(key1).isNotEqualTo(key2);
        }
    }

    @Nested
    @DisplayName("businessKey 생성")
    class BusinessKey {

        @Test
        @DisplayName("사업장 ID로 캐시 키를 생성한다")
        void generatesBusinessKey() {
            // when
            String key = GridCacheKeyGenerator.businessKey(42L);

            // then
            assertThat(key).isEqualTo("business:42");
        }
    }

    @Nested
    @DisplayName("adjacentSearchKeys - 3x3 Grid 키 생성")
    class AdjacentKeys {

        @Test
        @DisplayName("중심 좌표 기준 3x3 인접 Grid 캐시 키 9개를 반환한다")
        void returnsNineAdjacentKeys() {
            // given
            double lat = 37.5665;
            double lng = 126.9780;

            // when
            var keys = GridCacheKeyGenerator.adjacentSearchKeyPatterns(lat, lng);

            // then
            assertThat(keys).hasSize(9);
        }

        @Test
        @DisplayName("중심 Grid 키가 인접 키 목록에 포함된다")
        void includesCenterGrid() {
            // given
            double lat = 37.5665;
            double lng = 126.9780;

            // when
            var keys = GridCacheKeyGenerator.adjacentSearchKeyPatterns(lat, lng);

            // then
            assertThat(keys).contains("search:37.57:126.98:*");
        }
    }

    @Nested
    @DisplayName("Privacy 검증")
    class Privacy {

        @Test
        @DisplayName("캐시 키에 원본 좌표가 아닌 반올림 좌표만 포함된다")
        void keyContainsOnlyRoundedCoordinates() {
            // given
            double originalLat = 37.56789;
            double originalLng = 126.97654;

            // when
            String key = GridCacheKeyGenerator.searchKey(originalLat, originalLng, 1.0);

            // then
            assertThat(key).doesNotContain("37.56789");
            assertThat(key).doesNotContain("126.97654");
            assertThat(key).contains("37.57");
            assertThat(key).contains("126.98");
        }

        @Test
        @DisplayName("반올림된 좌표에서 원본 좌표를 복원할 수 없다 (정밀도 손실)")
        void cannotRecoverOriginalFromRoundedKey() {
            // given
            double original1 = 37.5641;
            double original2 = 37.5649;

            // when
            String key1 = GridCacheKeyGenerator.searchKey(original1, 126.98, 1.0);
            String key2 = GridCacheKeyGenerator.searchKey(original2, 126.98, 1.0);

            // then
            assertThat(key1).isEqualTo(key2);
        }
    }
}
