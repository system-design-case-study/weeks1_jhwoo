package com.proximity.adapter.out.persistence.read;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * jOOQ에서 PostGIS 함수를 사용하기 위한 유틸리티 클래스.
 */
public final class PostgisFunction {

    private PostgisFunction() {
    }

    /**
     * ST_MakePoint(lng, lat)::geography 생성.
     */
    public static Field<Object> stMakePoint(double longitude, double latitude) {
        return DSL.field(
                "ST_MakePoint({0}, {1})::geography",
                Object.class,
                DSL.val(longitude),
                DSL.val(latitude)
        );
    }

    /**
     * ST_DWithin(location, point, radiusMeters) 조건 생성.
     */
    public static Condition stDWithin(Field<?> location, Field<?> point, double radiusMeters) {
        return DSL.condition(
                "ST_DWithin({0}, {1}, {2})",
                location,
                point,
                DSL.val(radiusMeters)
        );
    }

    /**
     * ST_Distance(location, point) 필드 생성 (미터 단위).
     */
    public static Field<Double> stDistance(Field<?> location, Field<?> point) {
        return DSL.field(
                "ST_Distance({0}, {1})",
                Double.class,
                location,
                point
        );
    }

    /**
     * ST_MakeEnvelope(xmin, ymin, xmax, ymax, SRID) Bounding Box geometry 생성.
     */
    public static Field<Object> stMakeEnvelope(double swLng, double swLat,
                                                double neLng, double neLat, int srid) {
        return DSL.field(
                "ST_MakeEnvelope({0}, {1}, {2}, {3}, {4})",
                Object.class,
                DSL.val(swLng),
                DSL.val(swLat),
                DSL.val(neLng),
                DSL.val(neLat),
                DSL.val(srid)
        );
    }

    /**
     * Bounding Box 교집합 조건 (GiST 인덱스 활용).
     */
    public static Condition bboxIntersects(Field<?> location, Field<?> envelope) {
        return DSL.condition(
                "{0} && {1}::geography",
                location,
                envelope
        );
    }
}
