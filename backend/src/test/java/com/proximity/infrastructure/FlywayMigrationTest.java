package com.proximity.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("proximity_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.primary.jdbc-url", postgres::getJdbcUrl);
        registry.add("spring.datasource.primary.username", postgres::getUsername);
        registry.add("spring.datasource.primary.password", postgres::getPassword);
        registry.add("spring.datasource.replica.jdbc-url", postgres::getJdbcUrl);
        registry.add("spring.datasource.replica.username", postgres::getUsername);
        registry.add("spring.datasource.replica.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16379");
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 마이그레이션 후 모든 테이블이 존재해야 한다")
    void allTablesShouldExist() {
        // given
        List<String> expectedTables = List.of("owners", "businesses", "business_hours", "business_photos");

        // when
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                        "AND table_name NOT LIKE 'flyway%'",
                String.class
        );

        // then
        assertThat(actualTables).containsAll(expectedTables);
    }

    @Test
    @DisplayName("businesses 테이블에 GIST 인덱스가 존재해야 한다")
    void gistIndexShouldExist() {
        // given & when
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(" +
                        "SELECT 1 FROM pg_indexes " +
                        "WHERE tablename = 'businesses' AND indexname = 'idx_business_location'" +
                        ")",
                Boolean.class
        );

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("businesses 테이블에 중복 방지 UNIQUE 제약 조건이 존재해야 한다")
    void uniqueConstraintShouldExist() {
        // given & when
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(" +
                        "SELECT 1 FROM information_schema.table_constraints " +
                        "WHERE table_name = 'businesses' AND constraint_type = 'UNIQUE' " +
                        "AND constraint_name LIKE '%owner_id_name_latitude_longitude%'" +
                        ")",
                Boolean.class
        );

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("location 자동 생성 Trigger가 존재해야 한다")
    void locationTriggerShouldExist() {
        // given & when
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(" +
                        "SELECT 1 FROM information_schema.triggers " +
                        "WHERE trigger_name = 'trg_business_location'" +
                        ")",
                Boolean.class
        );

        // then
        assertThat(exists).isTrue();
    }
}
