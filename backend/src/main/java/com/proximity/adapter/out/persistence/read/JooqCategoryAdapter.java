package com.proximity.adapter.out.persistence.read;

import com.proximity.application.port.out.CategoryPort;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.proximity.adapter.out.persistence.jooq.Tables.BUSINESSES;

@Repository
public class JooqCategoryAdapter implements CategoryPort {

    private final DSLContext dsl;

    public JooqCategoryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<String> findDistinctCategories() {
        return dsl
                .selectDistinct(BUSINESSES.CATEGORY)
                .from(BUSINESSES)
                .where(BUSINESSES.CATEGORY.isNotNull())
                .orderBy(BUSINESSES.CATEGORY)
                .fetchInto(String.class);
    }
}
