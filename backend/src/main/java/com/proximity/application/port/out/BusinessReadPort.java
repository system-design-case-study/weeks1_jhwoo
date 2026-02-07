package com.proximity.application.port.out;

import com.proximity.application.dto.BusinessDetailResponse;

import java.util.Optional;

public interface BusinessReadPort {

    Optional<BusinessDetailResponse> findById(Long id);
}
