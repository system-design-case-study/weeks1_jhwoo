package com.proximity.infrastructure;

import com.proximity.testconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PostgisExtensionTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PostGIS extension이 설치되어 있어야 한다")
    void postgisExtensionShouldExist() {
        // given & when
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'postgis')",
                Boolean.class
        );

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("PostGIS 공간 함수가 동작해야 한다")
    void postgisSpatialFunctionsShouldWork() {
        // given & when
        Double distance = jdbcTemplate.queryForObject(
                "SELECT ST_Distance(" +
                        "ST_MakePoint(127.0, 37.5)::geography, " +
                        "ST_MakePoint(127.1, 37.6)::geography" +
                        ")",
                Double.class
        );

        // then
        assertThat(distance).isGreaterThan(0);
    }
}
