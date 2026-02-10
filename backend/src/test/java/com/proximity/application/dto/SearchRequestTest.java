package com.proximity.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청이면 검증을 통과한다")
    void validRequest() {
        // given
        SearchRequest request = new SearchRequest(37.5665, 126.9780, 1000.0, null, 0, 20);

        // when
        Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request);

        // then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("위도가 null이면 검증에 실패한다")
    void nullLatitude() {
        // given
        SearchRequest request = new SearchRequest(null, 126.9780, 1000.0, null, 0, 20);

        // when
        Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("위도"));
    }

    @Test
    @DisplayName("경도가 null이면 검증에 실패한다")
    void nullLongitude() {
        // given
        SearchRequest request = new SearchRequest(37.5665, null, 1000.0, null, 0, 20);

        // when
        Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("경도"));
    }

    @Test
    @DisplayName("반경이 null이면 검증에 실패한다")
    void nullRadius() {
        // given
        SearchRequest request = new SearchRequest(37.5665, 126.9780, null, null, 0, 20);

        // when
        Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("반경"));
    }

    @Test
    @DisplayName("page와 size가 null이면 기본값이 적용된다")
    void defaultPageAndSize() {
        // given
        SearchRequest request = new SearchRequest(37.5665, 126.9780, 1000.0, null, null, null);

        // when & then
        assertEquals(0, request.page());
        assertEquals(200, request.size());
    }

    @Test
    @DisplayName("위도가 범위를 초과하면 검증에 실패한다")
    void latitudeOutOfRange() {
        // given
        SearchRequest request = new SearchRequest(91.0, 126.9780, 1000.0, null, 0, 20);

        // when
        Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("경도가 범위를 초과하면 검증에 실패한다")
    void longitudeOutOfRange() {
        // given
        SearchRequest request = new SearchRequest(37.5665, 181.0, 1000.0, null, 0, 20);

        // when
        Set<ConstraintViolation<SearchRequest>> violations = validator.validate(request);

        // then
        assertEquals(1, violations.size());
    }

    @Test
    @DisplayName("빈 문자열 category는 null로 정규화된다")
    void blankCategoryNormalized() {
        // given
        SearchRequest request = new SearchRequest(37.5665, 126.9780, 1.0, "  ", 0, 200);

        // when & then
        assertNull(request.category());
    }

    @Test
    @DisplayName("category가 있으면 그대로 유지된다")
    void categoryPreserved() {
        // given
        SearchRequest request = new SearchRequest(37.5665, 126.9780, 1.0, "카페", 0, 200);

        // when & then
        assertEquals("카페", request.category());
    }
}
