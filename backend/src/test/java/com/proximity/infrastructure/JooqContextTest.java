package com.proximity.infrastructure;

import com.proximity.testconfig.AbstractIntegrationTest;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class JooqContextTest extends AbstractIntegrationTest {

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
