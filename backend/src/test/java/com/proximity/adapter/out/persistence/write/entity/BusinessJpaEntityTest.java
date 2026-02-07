package com.proximity.adapter.out.persistence.write.entity;

import com.proximity.domain.Business;
import com.proximity.domain.BusinessHours;
import com.proximity.domain.BusinessPhoto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessJpaEntityTest {

    @Test
    @DisplayName("Business Domain → JPA Entity → Domain 왕복 변환 정합성")
    void roundTripConversion() {
        // given
        Business original = new Business("테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                "02-1234-5678", "카페", 1L);
        original.setId(100L);
        original.setCreatedAt(OffsetDateTime.now());
        original.setUpdatedAt(OffsetDateTime.now());

        BusinessHours hours = new BusinessHours(100L, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        hours.setId(10L);
        original.setBusinessHours(List.of(hours));

        BusinessPhoto photo = new BusinessPhoto(100L, "https://example.com/photo.jpg", 1);
        photo.setId(20L);
        photo.setCreatedAt(OffsetDateTime.now());
        original.setPhotos(List.of(photo));

        // when
        BusinessJpaEntity entity = BusinessJpaEntity.fromDomain(original);
        Business restored = entity.toDomain();

        // then
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getAddress()).isEqualTo(original.getAddress());
        assertThat(restored.getLatitude()).isEqualTo(original.getLatitude());
        assertThat(restored.getLongitude()).isEqualTo(original.getLongitude());
        assertThat(restored.getPhone()).isEqualTo(original.getPhone());
        assertThat(restored.getCategory()).isEqualTo(original.getCategory());
        assertThat(restored.getOwnerId()).isEqualTo(original.getOwnerId());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Test
    @DisplayName("JPA Entity의 location 필드가 올바르게 생성된다")
    void locationFieldCreated() {
        // given
        Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);

        // when
        BusinessJpaEntity entity = BusinessJpaEntity.fromDomain(business);

        // then
        assertThat(entity.getLocation()).isNotNull();
        assertThat(entity.getLocation().getX()).isEqualTo(126.9780);
        assertThat(entity.getLocation().getY()).isEqualTo(37.5665);
        assertThat(entity.getLocation().getSRID()).isEqualTo(4326);
    }

    @Test
    @DisplayName("BusinessHours 변환 정합성")
    void businessHoursConversion() {
        // given
        Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);
        business.setId(1L);
        BusinessHours hours = new BusinessHours(1L, 1, LocalTime.of(10, 0), LocalTime.of(22, 0), false);
        hours.setId(5L);
        business.setBusinessHours(List.of(hours));

        // when
        BusinessJpaEntity entity = BusinessJpaEntity.fromDomain(business);
        Business restored = entity.toDomain();

        // then
        assertThat(restored.getBusinessHours()).hasSize(1);
        BusinessHours restoredHours = restored.getBusinessHours().get(0);
        assertThat(restoredHours.getDayOfWeek()).isEqualTo(1);
        assertThat(restoredHours.getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(restoredHours.getCloseTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(restoredHours.isClosed()).isFalse();
    }

    @Test
    @DisplayName("BusinessPhoto 변환 정합성")
    void businessPhotoConversion() {
        // given
        Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);
        business.setId(1L);
        BusinessPhoto photo = new BusinessPhoto(1L, "https://example.com/img.jpg", 0);
        photo.setId(3L);
        photo.setCreatedAt(OffsetDateTime.now());
        business.setPhotos(List.of(photo));

        // when
        BusinessJpaEntity entity = BusinessJpaEntity.fromDomain(business);
        Business restored = entity.toDomain();

        // then
        assertThat(restored.getPhotos()).hasSize(1);
        BusinessPhoto restoredPhoto = restored.getPhotos().get(0);
        assertThat(restoredPhoto.getPhotoUrl()).isEqualTo("https://example.com/img.jpg");
        assertThat(restoredPhoto.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("null 컬렉션 처리")
    void nullCollections() {
        // given
        Business business = new Business("테스트", "주소", 37.5665, 126.9780, null, null, 1L);

        // when
        BusinessJpaEntity entity = BusinessJpaEntity.fromDomain(business);
        Business restored = entity.toDomain();

        // then
        assertThat(restored.getBusinessHours()).isEmpty();
        assertThat(restored.getPhotos()).isEmpty();
    }
}
