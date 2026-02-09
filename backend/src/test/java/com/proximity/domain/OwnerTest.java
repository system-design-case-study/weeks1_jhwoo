package com.proximity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerTest {

    @Test
    @DisplayName("생성자 호출 시 필드가 올바르게 설정되어야 한다")
    void constructor_setsFields() {
        // when
        Owner owner = new Owner("test@example.com", "$2a$10$hashedPassword", "테스트");

        // then
        assertThat(owner.getEmail()).isEqualTo("test@example.com");
        assertThat(owner.getPasswordHash()).isEqualTo("$2a$10$hashedPassword");
        assertThat(owner.getName()).isEqualTo("테스트");
    }

    @Test
    @DisplayName("생성 시 타임스탬프가 초기화되어야 한다")
    void constructor_initializesTimestamps() {
        // when
        Owner owner = new Owner("test@example.com", "$2a$10$hashedPassword", "테스트");

        // then
        assertThat(owner.getCreatedAt()).isNotNull();
        assertThat(owner.getUpdatedAt()).isNotNull();
    }
}
