package com.proximity.adapter.out.persistence.write.entity;

import com.proximity.domain.Owner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerJpaEntityTest {

    @Test
    @DisplayName("Owner Domain → JPA Entity → Domain 왕복 변환 정합성")
    void roundTripConversion() {
        // given
        Owner original = new Owner("test@example.com", "hashed_pw", "테스트 사장");
        original.setId(1L);
        original.setCreatedAt(OffsetDateTime.now());
        original.setUpdatedAt(OffsetDateTime.now());

        // when
        OwnerJpaEntity entity = OwnerJpaEntity.fromDomain(original);
        Owner restored = entity.toDomain();

        // then
        assertThat(restored.getEmail()).isEqualTo(original.getEmail());
        assertThat(restored.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }
}
