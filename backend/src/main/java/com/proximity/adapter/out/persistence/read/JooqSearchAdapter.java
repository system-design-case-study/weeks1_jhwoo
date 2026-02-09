package com.proximity.adapter.out.persistence.read;

import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.port.out.SearchPort;
import io.micrometer.core.instrument.Timer;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.proximity.adapter.out.persistence.jooq.Tables.BUSINESSES;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.bboxIntersects;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.stDWithin;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.stDistance;
import static com.proximity.adapter.out.persistence.read.PostgisFunction.stMakeEnvelope;
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
                                                   int page, int size) {
        return searchQueryDurationTimer.record(() -> {
            Field<Object> point = stMakePoint(lng, lat);
            Field<Double> distance = stDistance(BUSINESSES.LOCATION, point).as("distance");

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
                    .where(stDWithin(BUSINESSES.LOCATION, point, radiusMeters))
                    .orderBy(DSL.field("distance"))
                    .limit(size)
                    .offset(page * size)
                    .fetchInto(BusinessSummary.class);
        });
    }

    @Override
    public long countByLocation(double lat, double lng, double radiusMeters) {
        Field<Object> point = stMakePoint(lng, lat);

        return dsl
                .selectCount()
                .from(BUSINESSES)
                .where(stDWithin(BUSINESSES.LOCATION, point, radiusMeters))
                .fetchOne(0, long.class);
    }

    @Override
    public List<BusinessSummary> searchByBounds(double swLat, double swLng,
                                                 double neLat, double neLng,
                                                 double centerLat, double centerLng,
                                                 int size) {
        return searchQueryDurationTimer.record(() -> {
            Field<Object> envelope = stMakeEnvelope(swLng, swLat, neLng, neLat, 4326);
            Field<Object> centerPoint = stMakePoint(centerLng, centerLat);
            Field<Double> distance = stDistance(BUSINESSES.LOCATION, centerPoint).as("distance");

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
                    .where(bboxIntersects(BUSINESSES.LOCATION, envelope))
                    .orderBy(DSL.field("distance"))
                    .limit(size)
                    .fetchInto(BusinessSummary.class);
        });
    }
}
