package com.proximity.adapter.out.persistence.read;

import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.port.out.SearchPort;
import io.micrometer.core.instrument.Timer;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.proximity.adapter.out.persistence.jooq.Tables.BUSINESSES;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.stDWithin;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.stDistance;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.stMakePoint;

@Repository
public class JooqSearchAdapter implements SearchPort {

    private final DSLContext dsl;
    private final Timer searchQueryDurationTimer;

    public JooqSearchAdapter(DSLContext dsl, Timer searchQueryDurationTimer) {
        this.dsl = dsl;
        this.searchQueryDurationTimer = searchQueryDurationTimer;
    }

    @Override
    public List<BusinessSummary> searchByLocation(double lat, double lng, double radiusMeters,
                                                   String category, int page, int size) {
        return searchQueryDurationTimer.record(() -> {
            Field<Object> point = stMakePoint(lng, lat);
            Field<Double> distance = stDistance(BUSINESSES.LOCATION, point).as("distance");

            Condition where = stDWithin(BUSINESSES.LOCATION, point, radiusMeters);
            if (category != null) {
                where = where.and(BUSINESSES.CATEGORY.eq(category));
            }

            return dsl
                    .select(
                            BUSINESSES.ID,
                            BUSINESSES.NAME,
                            BUSINESSES.ADDRESS,
                            BUSINESSES.LATITUDE,
                            BUSINESSES.LONGITUDE,
                            distance,
                            BUSINESSES.CATEGORY
                    )
                    .from(BUSINESSES)
                    .where(where)
                    .orderBy(DSL.field("distance"))
                    .limit(size)
                    .offset(page * size)
                    .fetchInto(BusinessSummary.class);
        });
    }

    @Override
    public long countByLocation(double lat, double lng, double radiusMeters, String category) {
        Field<Object> point = stMakePoint(lng, lat);

        Condition where = stDWithin(BUSINESSES.LOCATION, point, radiusMeters);
        if (category != null) {
            where = where.and(BUSINESSES.CATEGORY.eq(category));
        }

        return dsl
                .selectCount()
                .from(BUSINESSES)
                .where(where)
                .fetchOne(0, long.class);
    }
}
