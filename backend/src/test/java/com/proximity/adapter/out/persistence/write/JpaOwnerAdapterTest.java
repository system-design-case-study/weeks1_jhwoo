package com.proximity.adapter.out.persistence.write;

import com.proximity.domain.Owner;
import com.proximity.testconfig.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class JpaOwnerAdapterTest extends AbstractIntegrationTest {

    @Autowired
    private JpaOwnerAdapter jpaOwnerAdapter;

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("신규 Owner 저장 시 ID가 생성되어야 한다")
        void save_newOwner_returnsWithId() {
            // given
            Owner owner = new Owner("save-test@example.com", "$2a$10$hashedPassword", "테스트 사용자");

            // when
            Owner saved = jpaOwnerAdapter.save(owner);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getEmail()).isEqualTo("save-test@example.com");
            assertThat(saved.getName()).isEqualTo("테스트 사용자");
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("존재하는 이메일로 조회하면 Owner를 반환한다")
        void findByEmail_existing_returnsOwner() {
            // given
            Owner owner = new Owner("find-test@example.com", "$2a$10$hashedPassword", "찾기 테스트");
            jpaOwnerAdapter.save(owner);

            // when
            Optional<Owner> result = jpaOwnerAdapter.findByEmail("find-test@example.com");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("find-test@example.com");
            assertThat(result.get().getName()).isEqualTo("찾기 테스트");
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 조회하면 빈 Optional을 반환한다")
        void findByEmail_notExisting_returnsEmpty() {
            // when
            Optional<Owner> result = jpaOwnerAdapter.findByEmail("nonexistent@example.com");

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByEmail")
    class ExistsByEmail {

        @Test
        @DisplayName("존재하는 이메일이면 true를 반환한다")
        void existsByEmail_existing_returnsTrue() {
            // given
            Owner owner = new Owner("exists-test@example.com", "$2a$10$hashedPassword", "존재 테스트");
            jpaOwnerAdapter.save(owner);

            // when
            boolean result = jpaOwnerAdapter.existsByEmail("exists-test@example.com");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 false를 반환한다")
        void existsByEmail_notExisting_returnsFalse() {
            // when
            boolean result = jpaOwnerAdapter.existsByEmail("nonexistent@example.com");

            // then
            assertThat(result).isFalse();
        }
    }
}
