package com.proximity.infrastructure;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JooqContextTest {

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
    private DSLContext dslContext;

    @Test
    @DisplayName("DSLContext Bean이 생성되어야 한다")
    void dslContextShouldBeCreated() {
        assertThat(dslContext).isNotNull();
    }

    @Test
    @DisplayName("DSLContext가 PostgreSQL dialect을 사용해야 한다")
    void dslContextShouldUsePostgresDialect() {
        assertThat(dslContext.dialect()).isEqualTo(SQLDialect.POSTGRES);
    }

    @Test
    @DisplayName("DSLContext로 간단한 쿼리를 실행할 수 있어야 한다")
    void dslContextShouldExecuteQuery() {
        // given & when
        Integer result = dslContext.selectOne().fetchOneInto(Integer.class);

        // then
        assertThat(result).isEqualTo(1);
    }
}
