package com.proximity.application.port.out;

import com.proximity.domain.Owner;

import java.util.Optional;

public interface OwnerPort {

    Owner save(Owner owner);

    Optional<Owner> findByEmail(String email);

    boolean existsByEmail(String email);
}
