package com.proximity.application.service;

import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.exception.InvalidRadiusException;
import com.proximity.application.port.out.CachePort;
import com.proximity.application.port.out.SearchPort;
import com.proximity.config.MetricsConfig.CacheHitRatioHolder;
import io.micrometer.core.instrument.DistributionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchPort searchPort;

    @Mock
    private CachePort cachePort;

    @Mock
    private DistributionSummary searchResultCountSummary;

    @Mock
    private DistributionSummary searchRadiusHistogram;

    @Mock
    private CacheHitRatioHolder cacheHitRatioHolder;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                searchPort, cachePort, searchResultCountSummary, searchRadiusHistogram, cacheHitRatioHolder);
    }

    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    @Nested
    @DisplayName("기본 검색")
    class BasicSearch {

        @Test
        @DisplayName("정상 검색 시 SearchResponse를 반환한다")
        void search_validRequest_returnsResponse() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            double expectedRadiusMeters = 1000.0;

            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.empty());

            List<BusinessSummary> businesses = List.of(
                    new BusinessSummary(1L, "카페A", "서울시 중구", 37.5665, 126.9780, 150.0, "카페"),
                    new BusinessSummary(2L, "식당B", "서울시 중구", 37.5670, 126.9790, 300.0, "식당")
            );

            when(searchPort.searchByLocation(eq(LATITUDE), eq(LONGITUDE), eq(expectedRadiusMeters), eq(0), eq(20)))
                    .thenReturn(businesses);
            when(searchPort.countByLocation(eq(LATITUDE), eq(LONGITUDE), eq(expectedRadiusMeters)))
                    .thenReturn(2L);

            // when
            SearchResponse response = searchService.search(request);

            // then
            assertThat(response.businesses()).hasSize(2);
            assertThat(response.total()).isEqualTo(2);
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 리스트와 total=0을 반환한다 (EC-1)")
        void search_noResults_returnsEmptyList() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 0.5, 0, 20);
            double expectedRadiusMeters = 500.0;

            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.empty());

            when(searchPort.searchByLocation(eq(LATITUDE), eq(LONGITUDE), eq(expectedRadiusMeters), eq(0), eq(20)))
                    .thenReturn(Collections.emptyList());
            when(searchPort.countByLocation(eq(LATITUDE), eq(LONGITUDE), eq(expectedRadiusMeters)))
                    .thenReturn(0L);

            // when
            SearchResponse response = searchService.search(request);

            // then
            assertThat(response.businesses()).isEmpty();
            assertThat(response.total()).isZero();
        }

        @Test
        @DisplayName("유효하지 않은 radius 값이면 InvalidRadiusException을 던진다 (FR-002)")
        void search_invalidRadius_throwsException() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 3.0, 0, 20);

            // when & then
            assertThatThrownBy(() -> searchService.search(request))
                    .isInstanceOf(InvalidRadiusException.class);
        }
    }

    @Nested
    @DisplayName("캐시 통합")
    class CacheIntegration {

        @Test
        @DisplayName("Cache Hit 시 SearchPort를 호출하지 않는다")
        void search_cacheHit_doesNotCallSearchPort() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            SearchResponse cachedResponse = new SearchResponse(
                    List.of(new BusinessSummary(1L, "카페A", "서울시 중구", 37.5665, 126.9780, 150.0, "카페")),
                    1L, 0, 20
            );

            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.of(cachedResponse));

            // when
            SearchResponse response = searchService.search(request);

            // then
            assertThat(response).isEqualTo(cachedResponse);
            verify(searchPort, never()).searchByLocation(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt());
            verify(searchPort, never()).countByLocation(anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("Cache Miss 시 SearchPort 호출 후 결과를 캐싱한다")
        void search_cacheMiss_callsSearchPortAndCaches() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            double expectedRadiusMeters = 1000.0;

            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.empty());

            List<BusinessSummary> businesses = List.of(
                    new BusinessSummary(1L, "카페A", "서울시 중구", 37.5665, 126.9780, 150.0, "카페")
            );
            when(searchPort.searchByLocation(eq(LATITUDE), eq(LONGITUDE), eq(expectedRadiusMeters), eq(0), eq(20)))
                    .thenReturn(businesses);
            when(searchPort.countByLocation(eq(LATITUDE), eq(LONGITUDE), eq(expectedRadiusMeters)))
                    .thenReturn(1L);

            // when
            SearchResponse response = searchService.search(request);

            // then
            assertThat(response.businesses()).hasSize(1);
            verify(cachePort).putSearchCache(anyString(), any(SearchResponse.class));
        }
    }

    @Nested
    @DisplayName("메트릭 기록")
    class MetricsRecording {

        @Test
        @DisplayName("검색 시 radius가 histogram에 기록된다")
        void search_recordsRadiusHistogram() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.empty());
            when(searchPort.searchByLocation(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(searchPort.countByLocation(anyDouble(), anyDouble(), anyDouble())).thenReturn(0L);

            // when
            searchService.search(request);

            // then
            verify(searchRadiusHistogram).record(1.0);
        }

        @Test
        @DisplayName("Cache Hit 시 cacheHitRatioHolder.recordHit()이 호출된다")
        void search_cacheHit_recordsHit() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            SearchResponse cachedResponse = new SearchResponse(Collections.emptyList(), 0L, 0, 20);
            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.of(cachedResponse));

            // when
            searchService.search(request);

            // then
            verify(cacheHitRatioHolder).recordHit();
            verify(cacheHitRatioHolder, never()).recordMiss();
        }

        @Test
        @DisplayName("Cache Miss 시 cacheHitRatioHolder.recordMiss()가 호출된다")
        void search_cacheMiss_recordsMiss() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.empty());
            when(searchPort.searchByLocation(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(searchPort.countByLocation(anyDouble(), anyDouble(), anyDouble())).thenReturn(0L);

            // when
            searchService.search(request);

            // then
            verify(cacheHitRatioHolder).recordMiss();
            verify(cacheHitRatioHolder, never()).recordHit();
        }

        @Test
        @DisplayName("Cache Miss 시 결과 건수가 searchResultCountSummary에 기록된다")
        void search_cacheMiss_recordsResultCount() {
            // given
            SearchRequest request = new SearchRequest(LATITUDE, LONGITUDE, 1.0, 0, 20);
            when(cachePort.getSearchCache(anyString())).thenReturn(Optional.empty());
            when(searchPort.searchByLocation(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(searchPort.countByLocation(anyDouble(), anyDouble(), anyDouble())).thenReturn(5L);

            // when
            searchService.search(request);

            // then
            verify(searchResultCountSummary).record(5L);
        }
    }
}
