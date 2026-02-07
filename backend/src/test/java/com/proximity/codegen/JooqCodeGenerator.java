package com.proximity.codegen;

import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class JooqCodeGenerator {

    public static void main(String[] args) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("proximity_codegen")
                .withUsername("codegen")
                .withPassword("codegen")) {

            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("filesystem:src/main/resources/db/migration")
                    .load()
                    .migrate();

            Configuration configuration = new Configuration()
                    .withJdbc(new Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(postgres.getJdbcUrl())
                            .withUser(postgres.getUsername())
                            .withPassword(postgres.getPassword()))
                    .withGenerator(new Generator()
                            .withDatabase(new Database()
                                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                                    .withInputSchema("public")
                                    .withIncludes("owners|businesses|business_hours|business_photos|update_business_location")
                                    .withExcludes("flyway_schema_history|spatial_ref_sys|geography_columns|geometry_columns"))
                            .withGenerate(new Generate()
                                    .withPojos(true)
                                    .withDaos(false)
                                    .withFluentSetters(true))
                            .withTarget(new Target()
                                    .withPackageName("com.proximity.adapter.out.persistence.jooq")
                                    .withDirectory("src/generated/java")));

            GenerationTool.generate(configuration);
        }
    }
}
