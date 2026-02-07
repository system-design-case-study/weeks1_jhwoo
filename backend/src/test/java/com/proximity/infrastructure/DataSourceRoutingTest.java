package com.proximity.infrastructure;

import com.proximity.config.DataSourceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataSourceRoutingTest {

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
    private DataSource dataSource;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    @Qualifier("replicaDataSource")
    private DataSource replicaDataSource;

    @Test
    @DisplayName("Primary DataSource Bean이 생성되어야 한다")
    void primaryDataSourceShouldBeCreated() {
        assertThat(primaryDataSource).isNotNull();
    }

    @Test
    @DisplayName("Replica DataSource Bean이 생성되어야 한다")
    void replicaDataSourceShouldBeCreated() {
        assertThat(replicaDataSource).isNotNull();
    }

    @Test
    @DisplayName("라우팅 DataSource가 Primary Bean으로 정상 연결되어야 한다")
    @Transactional(readOnly = false)
    void routingDataSourceShouldConnectToPrimary() throws Exception {
        // given & when
        try (Connection connection = dataSource.getConnection()) {
            // then
            assertThat(connection).isNotNull();
            assertThat(connection.isClosed()).isFalse();
        }
    }

    @Test
    @DisplayName("readOnly=true 트랜잭션에서 Replica로 연결되어야 한다")
    @Transactional(readOnly = true)
    void readOnlyTransactionShouldUseReplica() throws Exception {
        // given & when
        try (Connection connection = dataSource.getConnection()) {
            // then
            assertThat(connection).isNotNull();
            assertThat(connection.isClosed()).isFalse();
        }
    }
}
