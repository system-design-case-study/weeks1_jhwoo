package com.proximity.adapter.out.persistence.write;

import com.proximity.domain.Business;
import com.proximity.domain.BusinessHours;
import com.proximity.domain.Owner;
import com.proximity.testconfig.AbstractIntegrationTest;
import com.proximity.testconfig.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class JpaBusinessWriteAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private JpaBusinessWriteAdapter jpaBusinessWriteAdapter;

    @Autowired
    private JpaOwnerAdapter jpaOwnerAdapter;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        Owner owner = new Owner("biz-write-test@example.com", "$2a$10$hashedPassword", "테스트 사장");
        Owner saved = jpaOwnerAdapter.save(owner);
        ownerId = saved.getId();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("신규 사업장 저장 시 ID가 생성되어야 한다")
        void save_newBusiness_returnsWithId() {
            // given
            Business business = TestFixtures.createBusiness("저장 테스트 카페", 37.4979, 127.0276, ownerId);

            // when
            Business saved = jpaBusinessWriteAdapter.save(business);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo("저장 테스트 카페");
            assertThat(saved.getLatitude()).isEqualTo(37.4979);
            assertThat(saved.getLongitude()).isEqualTo(127.0276);
        }

        @Test
        @DisplayName("BusinessHours 포함 저장 시 영업시간도 저장되어야 한다")
        void save_withBusinessHours_savesHours() {
            // given
            Business business = TestFixtures.createBusiness("영업시간 테스트", 37.4979, 127.0276, ownerId);
            business.setBusinessHours(List.of(
                    new BusinessHours(null, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), false),
                    new BusinessHours(null, 6, null, null, true)
            ));

            // when
            Business saved = jpaBusinessWriteAdapter.save(business);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getBusinessHours()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByIdForWrite")
    class FindByIdForWrite {

        @Test
        @DisplayName("존재하는 ID로 조회하면 Business를 반환한다")
        void findByIdForWrite_existing_returnsBusiness() {
            // given
            Business business = TestFixtures.createBusiness("조회 테스트", 37.4979, 127.0276, ownerId);
            Business saved = jpaBusinessWriteAdapter.save(business);

            // when
            Optional<Business> result = jpaBusinessWriteAdapter.findByIdForWrite(saved.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("조회 테스트");
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
        void findByIdForWrite_notExisting_returnsEmpty() {
            // when
            Optional<Business> result = jpaBusinessWriteAdapter.findByIdForWrite(999999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("삭제 후 조회하면 빈 Optional을 반환한다")
        void deleteById_thenFindReturnsEmpty() {
            // given
            Business business = TestFixtures.createBusiness("삭제 테스트", 37.4979, 127.0276, ownerId);
            Business saved = jpaBusinessWriteAdapter.save(business);

            // when
            jpaBusinessWriteAdapter.deleteById(saved.getId());

            // then
            Optional<Business> result = jpaBusinessWriteAdapter.findByIdForWrite(saved.getId());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByOwnerAndNameAndLocation")
    class ExistsByOwnerAndNameAndLocation {

        @Test
        @DisplayName("동일 조합이 존재하면 true를 반환한다")
        void existsByOwnerAndNameAndLocation_existing_returnsTrue() {
            // given
            Business business = TestFixtures.createBusiness("중복 체크", 37.4979, 127.0276, ownerId);
            jpaBusinessWriteAdapter.save(business);

            // when
            boolean result = jpaBusinessWriteAdapter.existsByOwnerAndNameAndLocation(
                    ownerId, "중복 체크", 37.4979, 127.0276);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("동일 조합이 없으면 false를 반환한다")
        void existsByOwnerAndNameAndLocation_notExisting_returnsFalse() {
            // when
            boolean result = jpaBusinessWriteAdapter.existsByOwnerAndNameAndLocation(
                    ownerId, "없는 이름", 37.0, 127.0);

            // then
            assertThat(result).isFalse();
        }
    }
}
