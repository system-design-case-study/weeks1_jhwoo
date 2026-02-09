package com.proximity.adapter.out.persistence.read;

import com.proximity.application.dto.BusinessSummary;
import com.proximity.testconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JooqSearchAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private JooqSearchAdapter jooqSearchAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final double GANGNAM_LAT = 37.4979;
    private static final double GANGNAM_LNG = 127.0276;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM business_photos");
        jdbcTemplate.update("DELETE FROM business_hours");
        jdbcTemplate.update("DELETE FROM businesses");
        jdbcTemplate.update("DELETE FROM owners WHERE email LIKE 'search-test-%'");

        Long ownerId = jdbcTemplate.queryForObject(
                "INSERT INTO owners (email, password_hash, name) " +
                "VALUES ('search-test-' || gen_random_uuid() || '@test.com', '$2a$10$hash', '검색 테스트') " +
                "RETURNING id",
                Long.class);

        jdbcTemplate.update(
                "INSERT INTO businesses (owner_id, name, address, latitude, longitude, phone, category) " +
                "VALUES (?, '가까운 카페', '강남역 1번출구', 37.4980, 127.0277, '02-0001', '카페')",
                ownerId);

        jdbcTemplate.update(
                "INSERT INTO businesses (owner_id, name, address, latitude, longitude, phone, category) " +
                "VALUES (?, '중간 카페', '역삼역', 37.5007, 127.0365, '02-0002', '카페')",
                ownerId);

        jdbcTemplate.update(
                "INSERT INTO businesses (owner_id, name, address, latitude, longitude, phone, category) " +
                "VALUES (?, '먼 카페', '선릉역', 37.5044, 127.0491, '02-0003', '카페')",
                ownerId);

        jdbcTemplate.update(
                "INSERT INTO businesses (owner_id, name, address, latitude, longitude, phone, category) " +
                "VALUES (?, '부산 카페', '부산역', 35.1150, 129.0422, '051-0001', '카페')",
                ownerId);
    }

    @Nested
    @DisplayName("searchByLocation")
    class SearchByLocation {

        @Test
        @DisplayName("반경 내 사업장만 검색되어야 한다")
        void searchByLocation_withinRadius_returnsMatches() {
            // when
            List<BusinessSummary> result = jooqSearchAdapter.searchByLocation(
                    GANGNAM_LAT, GANGNAM_LNG, 1000, 0, 10);

            // then
            assertThat(result).isNotEmpty();
            assertThat(result).noneMatch(b -> b.name().equals("부산 카페"));
        }

        @Test
        @DisplayName("결과는 거리 순으로 정렬되어야 한다")
        void searchByLocation_orderedByDistance() {
            // when
            List<BusinessSummary> result = jooqSearchAdapter.searchByLocation(
                    GANGNAM_LAT, GANGNAM_LNG, 2000, 0, 10);

            // then
            assertThat(result.size()).isGreaterThanOrEqualTo(2);
            for (int i = 0; i < result.size() - 1; i++) {
                assertThat(result.get(i).distance())
                        .isLessThanOrEqualTo(result.get(i + 1).distance());
            }
        }

        @Test
        @DisplayName("페이징이 올바르게 동작해야 한다")
        void searchByLocation_pagination() {
            // when
            List<BusinessSummary> page0 = jooqSearchAdapter.searchByLocation(
                    GANGNAM_LAT, GANGNAM_LNG, 2000, 0, 2);
            List<BusinessSummary> page1 = jooqSearchAdapter.searchByLocation(
                    GANGNAM_LAT, GANGNAM_LNG, 2000, 1, 2);

            // then
            assertThat(page0).hasSize(2);
            assertThat(page1.size()).isLessThanOrEqualTo(2);

            if (!page1.isEmpty()) {
                assertThat(page0.get(0).id()).isNotEqualTo(page1.get(0).id());
            }
        }

        @Test
        @DisplayName("반경 밖 사업장은 제외되어야 한다")
        void searchByLocation_outsideRadius_excluded() {
            // when
            List<BusinessSummary> result = jooqSearchAdapter.searchByLocation(
                    GANGNAM_LAT, GANGNAM_LNG, 500, 0, 10);

            // then
            assertThat(result).noneMatch(b -> b.name().equals("부산 카페"));
            assertThat(result).noneMatch(b -> b.name().equals("먼 카페"));
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 리스트를 반환한다")
        void searchByLocation_noResults_returnsEmpty() {
            // when
            List<BusinessSummary> result = jooqSearchAdapter.searchByLocation(
                    0.0, 0.0, 1, 0, 10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByLocation")
    class CountByLocation {

        @Test
        @DisplayName("반경 내 사업장 수를 정확히 반환해야 한다")
        void countByLocation_returnsCorrectCount() {
            // when
            long count = jooqSearchAdapter.countByLocation(GANGNAM_LAT, GANGNAM_LNG, 2000);

            // then
            assertThat(count).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("결과가 없으면 0을 반환해야 한다")
        void countByLocation_noResults_returnsZero() {
            // when
            long count = jooqSearchAdapter.countByLocation(0.0, 0.0, 1);

            // then
            assertThat(count).isZero();
        }
    }
}
