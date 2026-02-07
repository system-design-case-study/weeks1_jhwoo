package com.proximity.adapter.out.persistence.write;

import com.proximity.adapter.out.persistence.write.entity.OwnerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerJpaRepository extends JpaRepository<OwnerJpaEntity, Long> {

    Optional<OwnerJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
