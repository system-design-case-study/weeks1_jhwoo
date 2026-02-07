package com.proximity.application.port.out;

import com.proximity.domain.Business;

import java.util.Optional;

public interface BusinessWritePort {

    Business save(Business business);

    void deleteById(Long id);

    Optional<Business> findByIdForWrite(Long id);

    boolean existsByOwnerAndNameAndLocation(Long ownerId, String name, double lat, double lng);
}
