package com.proximity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessHoursTest {

    @Nested
    @DisplayName("요일 검증")
    class DayOfWeekValidation {

        @ParameterizedTest
        @DisplayName("유효한 요일(0~6)로 생성 시 성공한다")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
        void validDayOfWeek(int dayOfWeek) {
            // given & when & then
            assertDoesNotThrow(() ->
                    new BusinessHours(1L, dayOfWeek, LocalTime.of(9, 0), LocalTime.of(18, 0), false)
            );
        }

        @ParameterizedTest
        @DisplayName("유효하지 않은 요일로 생성 시 실패한다")
        @ValueSource(ints = {-1, 7, 100, -100})
        void invalidDayOfWeek(int dayOfWeek) {
            // given & when & then
            assertThrows(IllegalArgumentException.class, () ->
                    new BusinessHours(1L, dayOfWeek, LocalTime.of(9, 0), LocalTime.of(18, 0), false)
            );
        }
    }

    @Nested
    @DisplayName("영업 여부 확인")
    class OpenCheck {

        @Test
        @DisplayName("영업 시간 내이면 true를 반환한다")
        void isOpenDuringBusinessHours() {
            // given
            BusinessHours hours = new BusinessHours(1L, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), false);

            // when & then
            assertTrue(hours.isOpen(LocalTime.of(12, 0)));
        }

        @Test
        @DisplayName("영업 시간 외이면 false를 반환한다")
        void isClosedOutsideBusinessHours() {
            // given
            BusinessHours hours = new BusinessHours(1L, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), false);

            // when & then
            assertFalse(hours.isOpen(LocalTime.of(20, 0)));
        }

        @Test
        @DisplayName("정기 휴무일이면 false를 반환한다")
        void isClosedOnClosedDay() {
            // given
            BusinessHours hours = new BusinessHours(1L, 6, LocalTime.of(9, 0), LocalTime.of(18, 0), true);

            // when & then
            assertFalse(hours.isOpen(LocalTime.of(12, 0)));
        }

        @Test
        @DisplayName("영업 시간이 null이면 false를 반환한다")
        void isClosedWhenTimesAreNull() {
            // given
            BusinessHours hours = new BusinessHours(1L, 0, null, null, false);

            // when & then
            assertFalse(hours.isOpen(LocalTime.of(12, 0)));
        }

        @Test
        @DisplayName("영업 시작/종료 경계 시간에도 영업 중이다")
        void isOpenAtBoundary() {
            // given
            BusinessHours hours = new BusinessHours(1L, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), false);

            // when & then
            assertTrue(hours.isOpen(LocalTime.of(9, 0)));
            assertTrue(hours.isOpen(LocalTime.of(18, 0)));
        }
    }
}
