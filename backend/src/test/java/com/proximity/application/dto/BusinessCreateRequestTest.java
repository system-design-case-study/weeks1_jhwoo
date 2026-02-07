package com.proximity.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCreateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청이면 검증을 통과한다")
    void validRequest() {
        // given
        BusinessCreateRequest request = new BusinessCreateRequest(
                "테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                "02-1234-5678", "카페", List.of()
        );

        // when
        Set<ConstraintViolation<BusinessCreateRequest>> violations = validator.validate(request);

        // then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("이름이 빈 문자열이면 검증에 실패한다")
    void blankName() {
        // given
        BusinessCreateRequest request = new BusinessCreateRequest(
                "", "서울시 강남구", 37.5665, 126.9780,
                null, null, null
        );

        // when
        Set<ConstraintViolation<BusinessCreateRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("사업장 이름"));
    }

    @Test
    @DisplayName("주소가 빈 문자열이면 검증에 실패한다")
    void blankAddress() {
        // given
        BusinessCreateRequest request = new BusinessCreateRequest(
                "테스트", "", 37.5665, 126.9780,
                null, null, null
        );

        // when
        Set<ConstraintViolation<BusinessCreateRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("주소"));
    }

    @Test
    @DisplayName("위도가 null이면 검증에 실패한다")
    void nullLatitude() {
        // given
        BusinessCreateRequest request = new BusinessCreateRequest(
                "테스트", "주소", null, 126.9780,
                null, null, null
        );

        // when
        Set<ConstraintViolation<BusinessCreateRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("위도"));
    }

    @Test
    @DisplayName("위도가 범위를 초과하면 검증에 실패한다")
    void latitudeOutOfRange() {
        // given
        BusinessCreateRequest request = new BusinessCreateRequest(
                "테스트", "주소", 91.0, 126.9780,
                null, null, null
        );

        // when
        Set<ConstraintViolation<BusinessCreateRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("경도가 범위를 초과하면 검증에 실패한다")
    void longitudeOutOfRange() {
        // given
        BusinessCreateRequest request = new BusinessCreateRequest(
                "테스트", "주소", 37.5665, 181.0,
                null, null, null
        );

        // when
        Set<ConstraintViolation<BusinessCreateRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
    }
}
