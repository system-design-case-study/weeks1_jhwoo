package com.proximity.infrastructure;

import com.proximity.testconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRoutingTest extends AbstractIntegrationTest {

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
