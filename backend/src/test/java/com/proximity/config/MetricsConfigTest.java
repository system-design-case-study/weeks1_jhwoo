package com.proximity.config;

import com.proximity.config.MetricsConfig.CacheHitRatioHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsConfigTest {

    private MeterRegistry registry;
    private MetricsConfig metricsConfig;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsConfig = new MetricsConfig();
    }

    @Nested
    @DisplayName("T-9.1: Custom Metrics 등록 테스트")
    class CustomMetricsRegistration {

        @Test
        @DisplayName("proximity.search.query.duration Timer가 등록된다")
        void searchQueryDurationTimerIsRegistered() {
            // when
            Timer timer = metricsConfig.searchQueryDurationTimer(registry);

            // then
            assertThat(timer).isNotNull();
            assertThat(registry.get("proximity.search.query.duration").timer()).isNotNull();
        }

        @Test
        @DisplayName("proximity.search.result.count Summary가 등록된다")
        void searchResultCountSummaryIsRegistered() {
            // when
            metricsConfig.searchResultCountSummary(registry);

            // then
            assertThat(registry.get("proximity.search.result.count").summary()).isNotNull();
        }

        @Test
        @DisplayName("proximity.search.radius Histogram이 등록된다")
        void searchRadiusHistogramIsRegistered() {
            // when
            metricsConfig.searchRadiusHistogram(registry);

            // then
            assertThat(registry.get("proximity.search.radius").summary()).isNotNull();
        }

        @Test
        @DisplayName("proximity.cache.hit.ratio Gauge가 등록된다")
        void cacheHitRatioGaugeIsRegistered() {
            // when
            metricsConfig.cacheHitRatioHolder(registry);

            // then
            assertThat(registry.get("proximity.cache.hit.ratio").gauge()).isNotNull();
        }

        @Test
        @DisplayName("Timer 기록 후 count가 증가한다")
        void timerRecordsCount() {
            // given
            Timer timer = metricsConfig.searchQueryDurationTimer(registry);

            // when
            timer.record(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // then
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }

        @Test
        @DisplayName("Summary 기록 후 count가 증가한다")
        void summaryRecordsCount() {
            // given
            var summary = metricsConfig.searchResultCountSummary(registry);

            // when
            summary.record(42);

            // then
            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.totalAmount()).isEqualTo(42.0);
        }
    }

    @Nested
    @DisplayName("T-9.2: Cache Hit Ratio Gauge 테스트")
    class CacheHitRatioGauge {

        @Test
        @DisplayName("초기 상태에서 ratio는 0.0이다")
        void initialRatioIsZero() {
            // given
            CacheHitRatioHolder holder = metricsConfig.cacheHitRatioHolder(registry);

            // then
            assertThat(holder.getRatio()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Cache Hit 발생 시 ratio가 증가한다")
        void hitIncreasesRatio() {
            // given
            CacheHitRatioHolder holder = metricsConfig.cacheHitRatioHolder(registry);

            // when
            holder.recordHit();

            // then
            assertThat(holder.getRatio()).isEqualTo(1.0);
            assertThat(holder.getHitCount()).isEqualTo(1);
            assertThat(holder.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Cache Miss 발생 시 ratio가 감소한다")
        void missDecreasesRatio() {
            // given
            CacheHitRatioHolder holder = metricsConfig.cacheHitRatioHolder(registry);

            // when
            holder.recordHit();
            holder.recordMiss();

            // then
            assertThat(holder.getRatio()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("혼합 시나리오에서 정확한 ratio를 계산한다")
        void mixedScenarioCalculatesCorrectRatio() {
            // given
            CacheHitRatioHolder holder = metricsConfig.cacheHitRatioHolder(registry);

            // when
            holder.recordHit();
            holder.recordHit();
            holder.recordHit();
            holder.recordMiss();
            holder.recordMiss();

            // then
            assertThat(holder.getRatio()).isEqualTo(0.6);
            assertThat(holder.getHitCount()).isEqualTo(3);
            assertThat(holder.getTotalCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Gauge 메트릭이 실시간 ratio를 반영한다")
        void gaugeReflectsRealtimeRatio() {
            // given
            CacheHitRatioHolder holder = metricsConfig.cacheHitRatioHolder(registry);

            // when
            holder.recordHit();
            double ratioAfterHit = registry.get("proximity.cache.hit.ratio").gauge().value();

            holder.recordMiss();
            double ratioAfterMiss = registry.get("proximity.cache.hit.ratio").gauge().value();

            // then
            assertThat(ratioAfterHit).isEqualTo(1.0);
            assertThat(ratioAfterMiss).isEqualTo(0.5);
        }
    }
}
