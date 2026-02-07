package com.proximity.adapter.out.persistence.read;

import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessDetailResponse.BusinessHoursDto;
import com.proximity.application.dto.BusinessDetailResponse.BusinessPhotoDto;
import com.proximity.application.port.out.BusinessReadPort;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.proximity.adapter.out.persistence.jooq.Tables.BUSINESSES;
import static com.proximity.adapter.out.persistence.jooq.Tables.BUSINESS_HOURS;
import static com.proximity.adapter.out.persistence.jooq.Tables.BUSINESS_PHOTOS;

@Repository
public class JooqBusinessReadAdapter implements BusinessReadPort {

    private final DSLContext dsl;

    public JooqBusinessReadAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<BusinessDetailResponse> findById(Long id) {
        Record businessRecord = dsl
                .select(
                        BUSINESSES.ID,
                        BUSINESSES.NAME,
                        BUSINESSES.ADDRESS,
                        BUSINESSES.LATITUDE,
                        BUSINESSES.LONGITUDE,
                        BUSINESSES.PHONE,
                        BUSINESSES.CATEGORY,
                        BUSINESSES.OWNER_ID,
                        BUSINESSES.CREATED_AT,
                        BUSINESSES.UPDATED_AT
                )
                .from(BUSINESSES)
                .where(BUSINESSES.ID.eq(id))
                .fetchOne();

        if (businessRecord == null) {
            return Optional.empty();
        }

        List<BusinessHoursDto> hours = fetchBusinessHours(id);
        List<BusinessPhotoDto> photos = fetchBusinessPhotos(id);

        return Optional.of(new BusinessDetailResponse(
                businessRecord.get(BUSINESSES.ID),
                businessRecord.get(BUSINESSES.NAME),
                businessRecord.get(BUSINESSES.ADDRESS),
                businessRecord.get(BUSINESSES.LATITUDE).doubleValue(),
                businessRecord.get(BUSINESSES.LONGITUDE).doubleValue(),
                businessRecord.get(BUSINESSES.PHONE),
                businessRecord.get(BUSINESSES.CATEGORY),
                businessRecord.get(BUSINESSES.OWNER_ID),
                hours,
                photos,
                businessRecord.get(BUSINESSES.CREATED_AT),
                businessRecord.get(BUSINESSES.UPDATED_AT)
        ));
    }

    private List<BusinessHoursDto> fetchBusinessHours(Long businessId) {
        var records = dsl
                .select(
                        BUSINESS_HOURS.ID,
                        BUSINESS_HOURS.DAY_OF_WEEK,
                        BUSINESS_HOURS.OPEN_TIME,
                        BUSINESS_HOURS.CLOSE_TIME,
                        BUSINESS_HOURS.IS_CLOSED
                )
                .from(BUSINESS_HOURS)
                .where(BUSINESS_HOURS.BUSINESS_ID.eq(businessId))
                .orderBy(BUSINESS_HOURS.DAY_OF_WEEK)
                .fetch();

        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        return records.map(r -> new BusinessHoursDto(
                r.get(BUSINESS_HOURS.ID),
                r.get(BUSINESS_HOURS.DAY_OF_WEEK).intValue(),
                r.get(BUSINESS_HOURS.OPEN_TIME),
                r.get(BUSINESS_HOURS.CLOSE_TIME),
                r.get(BUSINESS_HOURS.IS_CLOSED)
        ));
    }

    private List<BusinessPhotoDto> fetchBusinessPhotos(Long businessId) {
        var records = dsl
                .select(
                        BUSINESS_PHOTOS.ID,
                        BUSINESS_PHOTOS.PHOTO_URL,
                        BUSINESS_PHOTOS.DISPLAY_ORDER
                )
                .from(BUSINESS_PHOTOS)
                .where(BUSINESS_PHOTOS.BUSINESS_ID.eq(businessId))
                .orderBy(BUSINESS_PHOTOS.DISPLAY_ORDER)
                .fetch();

        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        return records.map(r -> new BusinessPhotoDto(
                r.get(BUSINESS_PHOTOS.ID),
                r.get(BUSINESS_PHOTOS.PHOTO_URL),
                r.get(BUSINESS_PHOTOS.DISPLAY_ORDER)
        ));
    }
}
