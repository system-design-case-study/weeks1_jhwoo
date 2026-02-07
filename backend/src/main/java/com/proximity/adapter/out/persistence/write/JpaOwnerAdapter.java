package com.proximity.adapter.out.persistence.write;

import com.proximity.adapter.out.persistence.write.entity.OwnerJpaEntity;
import com.proximity.application.port.out.OwnerPort;
import com.proximity.domain.Owner;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaOwnerAdapter implements OwnerPort {

    private final OwnerJpaRepository ownerJpaRepository;

    public JpaOwnerAdapter(OwnerJpaRepository ownerJpaRepository) {
        this.ownerJpaRepository = ownerJpaRepository;
    }

    @Override
    public Owner save(Owner owner) {
        OwnerJpaEntity entity = OwnerJpaEntity.fromDomain(owner);
        OwnerJpaEntity saved = ownerJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Owner> findByEmail(String email) {
        return ownerJpaRepository.findByEmail(email)
                .map(OwnerJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return ownerJpaRepository.existsByEmail(email);
    }
}
