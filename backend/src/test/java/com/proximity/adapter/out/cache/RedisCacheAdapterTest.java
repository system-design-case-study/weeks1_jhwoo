package com.proximity.adapter.out.cache;

import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.service.GridCacheKeyGenerator;
import com.proximity.testconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private RedisCacheAdapter redisCacheAdapter;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private SearchResponse createSearchResponse() {
        List<BusinessSummary> businesses = List.of(
                new BusinessSummary(1L, "테스트 카페", "강남구", 37.4979, 127.0276, 100.0, "카페")
        );
        return new SearchResponse(businesses, 1, 0, 10);
    }

    private BusinessDetailResponse createBusinessDetailResponse(Long id) {
        return new BusinessDetailResponse(
                id, "테스트 카페", "강남구", 37.4979, 127.0276,
                "02-1234-5678", "카페", 1L,
                Collections.emptyList(), Collections.emptyList(), null, null);
    }

    @Nested
    @DisplayName("SearchCache")
    class SearchCache {

        @Test
        @DisplayName("저장 후 조회하면 동일한 응답을 반환한다")
        void putAndGet_returnsSameResponse() {
            // given
            String key = GridCacheKeyGenerator.searchKey(37.50, 127.03, 1.0);
            SearchResponse response = createSearchResponse();

            // when
            redisCacheAdapter.putSearchCache(key, response);
            Optional<SearchResponse> result = redisCacheAdapter.getSearchCache(key);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().total()).isEqualTo(1);
            assertThat(result.get().businesses()).hasSize(1);
            assertThat(result.get().businesses().get(0).name()).isEqualTo("테스트 카페");
        }

        @Test
        @DisplayName("존재하지 않는 키로 조회하면 빈 Optional을 반환한다")
        void get_nonExistentKey_returnsEmpty() {
            // when
            Optional<SearchResponse> result = redisCacheAdapter.getSearchCache("search:nonexistent");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("저장 시 TTL이 설정되어야 한다")
        void put_setsTtl() {
            // given
            String key = GridCacheKeyGenerator.searchKey(37.50, 127.03, 1.0);
            SearchResponse response = createSearchResponse();

            // when
            redisCacheAdapter.putSearchCache(key, response);

            // then
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(600);
        }
    }

    @Nested
    @DisplayName("BusinessCache")
    class BusinessCache {

        @Test
        @DisplayName("저장 후 조회하면 동일한 응답을 반환한다")
        void putAndGet_returnsSameResponse() {
            // given
            Long id = 100L;
            BusinessDetailResponse response = createBusinessDetailResponse(id);

            // when
            redisCacheAdapter.putBusinessCache(id, response);
            Optional<BusinessDetailResponse> result = redisCacheAdapter.getBusinessCache(id);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(100L);
            assertThat(result.get().name()).isEqualTo("테스트 카페");
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
        void get_nonExistentId_returnsEmpty() {
            // when
            Optional<BusinessDetailResponse> result = redisCacheAdapter.getBusinessCache(999999L);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("저장 시 TTL이 설정되어야 한다")
        void put_setsTtl() {
            // given
            Long id = 100L;
            BusinessDetailResponse response = createBusinessDetailResponse(id);

            // when
            redisCacheAdapter.putBusinessCache(id, response);

            // then
            String key = GridCacheKeyGenerator.businessKey(id);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(1800);
        }
    }

    @Nested
    @DisplayName("invalidateSearchCache")
    class InvalidateSearchCache {

        @Test
        @DisplayName("무효화 후 해당 캐시가 삭제되어야 한다")
        void invalidate_removesCache() {
            // given
            double lat = 37.50;
            double lng = 127.03;
            String key = GridCacheKeyGenerator.searchKey(lat, lng, 1.0);
            redisCacheAdapter.putSearchCache(key, createSearchResponse());

            // when
            redisCacheAdapter.invalidateSearchCache(lat, lng);

            // then
            Optional<SearchResponse> result = redisCacheAdapter.getSearchCache(key);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("3x3 Grid 인접 키가 모두 삭제되어야 한다")
        void invalidate_removesAdjacentGridKeys() {
            // given
            double centerLat = 37.50;
            double centerLng = 127.03;

            List<String> patterns = GridCacheKeyGenerator.adjacentSearchKeyPatterns(centerLat, centerLng);
            for (String pattern : patterns) {
                String key = pattern.replace("*", "1.0");
                redisCacheAdapter.putSearchCache(key, createSearchResponse());
            }

            // when
            redisCacheAdapter.invalidateSearchCache(centerLat, centerLng);

            // then
            for (String pattern : patterns) {
                String key = pattern.replace("*", "1.0");
                assertThat(redisCacheAdapter.getSearchCache(key)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("invalidateBusinessCache")
    class InvalidateBusinessCache {

        @Test
        @DisplayName("무효화 후 해당 캐시가 삭제되어야 한다")
        void invalidate_removesCache() {
            // given
            Long id = 200L;
            redisCacheAdapter.putBusinessCache(id, createBusinessDetailResponse(id));

            // when
            redisCacheAdapter.invalidateBusinessCache(id);

            // then
            Optional<BusinessDetailResponse> result = redisCacheAdapter.getBusinessCache(id);
            assertThat(result).isEmpty();
        }
    }
}
