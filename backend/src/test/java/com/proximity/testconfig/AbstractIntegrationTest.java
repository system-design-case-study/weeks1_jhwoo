package com.proximity.testconfig;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.primary.jdbc-url", TestcontainersConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.primary.username", TestcontainersConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.primary.password", TestcontainersConfig.POSTGRES::getPassword);
        registry.add("spring.datasource.replica.jdbc-url", TestcontainersConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.replica.username", TestcontainersConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.replica.password", TestcontainersConfig.POSTGRES::getPassword);

        registry.add("spring.data.redis.host", TestcontainersConfig.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> TestcontainersConfig.REDIS.getMappedPort(6379));

        registry.add("jwt.secret",
                () -> "test-jwt-secret-key-must-be-at-least-256-bits-long-for-testing-purposes");
        registry.add("jwt.expiration-ms", () -> "3600000");
    }
}
