package com.proximity.adapter.out.persistence.write;

import com.proximity.adapter.out.persistence.write.entity.BusinessJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessJpaRepository extends JpaRepository<BusinessJpaEntity, Long> {

    boolean existsByOwnerIdAndNameAndLatitudeAndLongitude(
            Long ownerId, String name, double latitude, double longitude);
}
