package com.proximity.adapter.out.persistence.write;

import com.proximity.adapter.out.persistence.write.entity.BusinessJpaEntity;
import com.proximity.application.port.out.BusinessWritePort;
import com.proximity.domain.Business;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class JpaBusinessWriteAdapter implements BusinessWritePort {

    private final BusinessJpaRepository businessJpaRepository;

    public JpaBusinessWriteAdapter(BusinessJpaRepository businessJpaRepository) {
        this.businessJpaRepository = businessJpaRepository;
    }

    @Override
    public Business save(Business business) {
        BusinessJpaEntity entity = BusinessJpaEntity.fromDomain(business);
        BusinessJpaEntity saved = businessJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void deleteById(Long id) {
        businessJpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Business> findByIdForWrite(Long id) {
        return businessJpaRepository.findById(id)
                .map(BusinessJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOwnerAndNameAndLocation(Long ownerId, String name, double lat, double lng) {
        return businessJpaRepository.existsByOwnerIdAndNameAndLatitudeAndLongitude(
                ownerId, name, lat, lng);
    }
}
