package com.proximity.adapter.out.persistence.read;

import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.testconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JooqBusinessReadAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private JooqBusinessReadAdapter jooqBusinessReadAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long businessId;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM business_photos");
        jdbcTemplate.update("DELETE FROM business_hours");
        jdbcTemplate.update("DELETE FROM businesses");
        jdbcTemplate.update("DELETE FROM owners WHERE email LIKE 'read-test-%'");

        ownerId = jdbcTemplate.queryForObject(
                "INSERT INTO owners (email, password_hash, name) " +
                "VALUES ('read-test-' || gen_random_uuid() || '@test.com', '$2a$10$hash', '읽기 테스트') " +
                "RETURNING id",
                Long.class);

        businessId = jdbcTemplate.queryForObject(
                "INSERT INTO businesses (owner_id, name, address, latitude, longitude, phone, category) " +
                "VALUES (?, '읽기 테스트 카페', '서울시 강남구', 37.4979, 127.0276, '02-1234-5678', '카페') " +
                "RETURNING id",
                Long.class, ownerId);
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("존재하는 사업장 조회 시 모든 필드가 매핑되어야 한다")
        void findById_existing_returnsFullResponse() {
            // when
            Optional<BusinessDetailResponse> result = jooqBusinessReadAdapter.findById(businessId);

            // then
            assertThat(result).isPresent();
            BusinessDetailResponse response = result.get();
            assertThat(response.id()).isEqualTo(businessId);
            assertThat(response.name()).isEqualTo("읽기 테스트 카페");
            assertThat(response.address()).isEqualTo("서울시 강남구");
            assertThat(response.latitude()).isEqualTo(37.4979);
            assertThat(response.longitude()).isEqualTo(127.0276);
            assertThat(response.phone()).isEqualTo("02-1234-5678");
            assertThat(response.category()).isEqualTo("카페");
            assertThat(response.ownerId()).isEqualTo(ownerId);
        }

        @Test
        @DisplayName("영업시간이 있으면 day_of_week 순서로 반환한다")
        void findById_withHours_returnsSortedByDayOfWeek() {
            // given
            jdbcTemplate.update(
                    "INSERT INTO business_hours (business_id, day_of_week, open_time, close_time, is_closed) " +
                    "VALUES (?, 3, '10:00', '20:00', false)", businessId);
            jdbcTemplate.update(
                    "INSERT INTO business_hours (business_id, day_of_week, open_time, close_time, is_closed) " +
                    "VALUES (?, 0, '09:00', '18:00', false)", businessId);
            jdbcTemplate.update(
                    "INSERT INTO business_hours (business_id, day_of_week, open_time, close_time, is_closed) " +
                    "VALUES (?, 6, null, null, true)", businessId);

            // when
            Optional<BusinessDetailResponse> result = jooqBusinessReadAdapter.findById(businessId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().businessHours()).hasSize(3);
            assertThat(result.get().businessHours().get(0).dayOfWeek()).isEqualTo(0);
            assertThat(result.get().businessHours().get(1).dayOfWeek()).isEqualTo(3);
            assertThat(result.get().businessHours().get(2).dayOfWeek()).isEqualTo(6);
        }

        @Test
        @DisplayName("사진이 있으면 display_order 순서로 반환한다")
        void findById_withPhotos_returnsSortedByDisplayOrder() {
            // given
            jdbcTemplate.update(
                    "INSERT INTO business_photos (business_id, photo_url, display_order) " +
                    "VALUES (?, 'https://example.com/3.jpg', 3)", businessId);
            jdbcTemplate.update(
                    "INSERT INTO business_photos (business_id, photo_url, display_order) " +
                    "VALUES (?, 'https://example.com/1.jpg', 1)", businessId);

            // when
            Optional<BusinessDetailResponse> result = jooqBusinessReadAdapter.findById(businessId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().photos()).hasSize(2);
            assertThat(result.get().photos().get(0).displayOrder()).isEqualTo(1);
            assertThat(result.get().photos().get(1).displayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("영업시간/사진이 없으면 빈 리스트를 반환한다")
        void findById_noHoursNoPhotos_returnsEmptyLists() {
            // when
            Optional<BusinessDetailResponse> result = jooqBusinessReadAdapter.findById(businessId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().businessHours()).isEmpty();
            assertThat(result.get().photos()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
        void findById_notExisting_returnsEmpty() {
            // when
            Optional<BusinessDetailResponse> result = jooqBusinessReadAdapter.findById(999999L);

            // then
            assertThat(result).isEmpty();
        }
    }
}
