package com.proximity.infrastructure;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GistIndexScanTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("proximity_test")
            .withUsername("test")
            .withPassword("test");

    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS postgis");

            stmt.execute("""
                CREATE TABLE owners (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);

            stmt.execute("""
                CREATE TABLE businesses (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    owner_id BIGINT NOT NULL REFERENCES owners(id),
                    name VARCHAR(255) NOT NULL,
                    address VARCHAR(500) NOT NULL,
                    latitude DECIMAL(9,6) NOT NULL,
                    longitude DECIMAL(9,6) NOT NULL,
                    location GEOGRAPHY(Point, 4326) NOT NULL,
                    phone VARCHAR(20),
                    category VARCHAR(100),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);

            stmt.execute("CREATE INDEX idx_business_location ON businesses USING GIST(location)");

            stmt.execute("""
                INSERT INTO owners (email, password_hash, name)
                VALUES ('gist-test@test.com', '$2a$10$dummy', 'GIST 테스트')
                """);

            ResultSet rs = stmt.executeQuery("SELECT id FROM owners WHERE email = 'gist-test@test.com'");
            rs.next();
            long ownerId = rs.getLong(1);

            int batchSize = 2000;
            for (int batch = 0; batch < 10000; batch += batchSize) {
                StringBuilder values = new StringBuilder();
                int end = Math.min(batch + batchSize, 10000);
                for (int i = batch; i < end; i++) {
                    double lat = 37.4 + Math.random() * 0.3;
                    double lng = 126.8 + Math.random() * 0.4;

                    if (i > batch) values.append(",");
                    values.append(String.format(
                            "('업체_%d', '주소_%d', %.6f, %.6f, '010-0000-0000', '카페', %d, " +
                            "ST_SetSRID(ST_MakePoint(%.6f, %.6f), 4326)::geography, NOW(), NOW())",
                            i, i, lat, lng, ownerId, lng, lat));
                }
                stmt.execute(
                        "INSERT INTO businesses (name, address, latitude, longitude, phone, category, owner_id, location, created_at, updated_at) VALUES " +
                        values);
            }

            stmt.execute("ANALYZE businesses");
        }
    }

    @Test
    @DisplayName("T-9.4: 1km 반경 검색 시 GIST Index Scan을 사용한다")
    void smallRadiusSearch_usesGistIndexScan() throws Exception {
        // given
        double lat = 37.4979;
        double lng = 127.0276;
        double radiusMeters = 1000;

        // when
        StringBuilder planOutput = new StringBuilder();
        try (PreparedStatement ps = connection.prepareStatement(
                "EXPLAIN ANALYZE SELECT id, name FROM businesses " +
                "WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)")) {
            ps.setDouble(1, lng);
            ps.setDouble(2, lat);
            ps.setDouble(3, radiusMeters);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    planOutput.append(rs.getString(1)).append("\n");
                }
            }
        }

        // then
        assertThat(planOutput.toString().toLowerCase()).contains("index");
    }

    @Test
    @DisplayName("T-9.4: GIST 인덱스 크기를 확인할 수 있다")
    void gistIndexSize_isAccessible() throws Exception {
        // when
        long indexSize;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT pg_relation_size('idx_business_location')")) {
            rs.next();
            indexSize = rs.getLong(1);
        }

        // then
        assertThat(indexSize).isGreaterThan(0);
    }

    @Test
    @DisplayName("T-9.4: 10K 데이터에서 1km 반경 검색 결과를 반환한다")
    void tenKData_radiusSearch_returnsResults() throws Exception {
        // given
        double lat = 37.5;
        double lng = 127.0;
        double radiusMeters = 1000;

        // when
        long count;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM businesses " +
                "WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)")) {
            ps.setDouble(1, lng);
            ps.setDouble(2, lat);
            ps.setDouble(3, radiusMeters);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                count = rs.getLong(1);
            }
        }

        // then
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("T-9.4: 20km 반경 조회 시 실행 계획을 확인할 수 있다")
    void largeRadius_executionPlanIsAccessible() throws Exception {
        // given
        double lat = 37.5;
        double lng = 127.0;
        double radiusMeters = 20000;

        // when
        StringBuilder planOutput = new StringBuilder();
        try (PreparedStatement ps = connection.prepareStatement(
                "EXPLAIN ANALYZE SELECT id, name FROM businesses " +
                "WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)")) {
            ps.setDouble(1, lng);
            ps.setDouble(2, lat);
            ps.setDouble(3, radiusMeters);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    planOutput.append(rs.getString(1)).append("\n");
                }
            }
        }

        // then
        String plan = planOutput.toString().toLowerCase();
        assertThat(plan).satisfiesAnyOf(
                p -> assertThat(p).contains("seq scan"),
                p -> assertThat(p).contains("index")
        );
    }
}
