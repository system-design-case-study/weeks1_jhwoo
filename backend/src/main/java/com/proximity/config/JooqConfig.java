package com.proximity.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dslContext(@Qualifier("replicaDataSource") DataSource replicaDataSource) {
        return DSL.using(replicaDataSource, SQLDialect.POSTGRES);
    }
}
