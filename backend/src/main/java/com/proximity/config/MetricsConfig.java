package com.proximity.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    @Bean
    public Timer searchQueryDurationTimer(MeterRegistry registry) {
        return Timer.builder("proximity.search.query.duration")
                .description("ST_DWithin 쿼리 실행 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public DistributionSummary searchResultCountSummary(MeterRegistry registry) {
        return DistributionSummary.builder("proximity.search.result.count")
                .description("검색 결과 건수")
                .register(registry);
    }

    @Bean
    public DistributionSummary searchRadiusHistogram(MeterRegistry registry) {
        return DistributionSummary.builder("proximity.search.radius")
                .description("요청 반경 분포 (km)")
                .register(registry);
    }

    @Bean
    public CacheHitRatioHolder cacheHitRatioHolder(MeterRegistry registry) {
        CacheHitRatioHolder holder = new CacheHitRatioHolder();
        io.micrometer.core.instrument.Gauge.builder("proximity.cache.hit.ratio", holder, CacheHitRatioHolder::getRatio)
                .description("캐시 적중률")
                .register(registry);
        return holder;
    }

    public static class CacheHitRatioHolder {

        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong totalCount = new AtomicLong(0);

        public void recordHit() {
            hitCount.incrementAndGet();
            totalCount.incrementAndGet();
        }

        public void recordMiss() {
            totalCount.incrementAndGet();
        }

        public double getRatio() {
            long total = totalCount.get();
            if (total == 0) {
                return 0.0;
            }
            return (double) hitCount.get() / total;
        }

        public long getHitCount() {
            return hitCount.get();
        }

        public long getTotalCount() {
            return totalCount.get();
        }
    }
}
