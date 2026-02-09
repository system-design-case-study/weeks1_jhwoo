package com.proximity.testconfig;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class TestcontainersConfig {

    public static final PostgreSQLContainer<?> POSTGRES;
    public static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4")
                        .asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("proximity_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS.start();
    }
}
