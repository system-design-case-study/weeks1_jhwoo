package com.proximity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessPhotoTest {

    @Test
    @DisplayName("생성자 호출 시 필드가 올바르게 설정되어야 한다")
    void constructor_setsFields() {
        // when
        BusinessPhoto photo = new BusinessPhoto(1L, "https://example.com/photo.jpg", 3);

        // then
        assertThat(photo.getBusinessId()).isEqualTo(1L);
        assertThat(photo.getPhotoUrl()).isEqualTo("https://example.com/photo.jpg");
        assertThat(photo.getDisplayOrder()).isEqualTo(3);
    }
}
