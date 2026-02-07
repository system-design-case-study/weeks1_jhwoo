package com.proximity.application.port.in;

import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessUpdateRequest;

public interface BusinessUseCase {

    BusinessDetailResponse getDetail(Long id);

    BusinessDetailResponse create(BusinessCreateRequest request, Long ownerId);

    BusinessDetailResponse update(Long id, BusinessUpdateRequest request, Long ownerId);

    void delete(Long id, Long ownerId);
}
