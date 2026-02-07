package com.proximity.application.service;

import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessUpdateRequest;
import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.port.in.BusinessUseCase;
import com.proximity.application.port.out.BusinessReadPort;
import com.proximity.application.port.out.BusinessWritePort;
import com.proximity.application.port.out.CachePort;
import com.proximity.domain.Business;
import com.proximity.domain.BusinessHours;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class BusinessService implements BusinessUseCase {

    private final BusinessWritePort businessWritePort;
    private final BusinessReadPort businessReadPort;
    private final CachePort cachePort;

    public BusinessService(BusinessWritePort businessWritePort,
                           BusinessReadPort businessReadPort,
                           CachePort cachePort) {
        this.businessWritePort = businessWritePort;
        this.businessReadPort = businessReadPort;
        this.cachePort = cachePort;
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessDetailResponse getDetail(Long id) {
        return cachePort.getBusinessCache(id)
                .orElseGet(() -> {
                    BusinessDetailResponse response = businessReadPort.findById(id)
                            .orElseThrow(() -> new BusinessNotFoundException(id));
                    cachePort.putBusinessCache(id, response);
                    return response;
                });
    }

    @Override
    public BusinessDetailResponse create(BusinessCreateRequest request, Long ownerId) {
        boolean exists = businessWritePort.existsByOwnerAndNameAndLocation(
                ownerId, request.name(), request.latitude(), request.longitude());
        if (exists) {
            throw new DuplicateBusinessException();
        }

        Business business = new Business(
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.phone(),
                request.category(),
                ownerId
        );

        if (request.businessHours() != null) {
            List<BusinessHours> hours = request.businessHours().stream()
                    .map(h -> new BusinessHours(
                            null,
                            h.dayOfWeek(),
                            h.openTime() != null ? LocalTime.parse(h.openTime()) : null,
                            h.closeTime() != null ? LocalTime.parse(h.closeTime()) : null,
                            h.closed()
                    ))
                    .toList();
            business.setBusinessHours(hours);
        } else {
            business.setBusinessHours(Collections.emptyList());
        }

        Business saved = businessWritePort.save(business);
        cachePort.invalidateSearchCache(saved.getLatitude(), saved.getLongitude());
        return toDetailResponse(saved);
    }

    @Override
    public BusinessDetailResponse update(Long id, BusinessUpdateRequest request, Long ownerId) {
        Business business = businessWritePort.findByIdForWrite(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));

        if (!business.isOwnedBy(ownerId)) {
            throw new BusinessOwnershipException();
        }

        if (request.name() != null) {
            business.setName(request.name());
        }
        if (request.address() != null) {
            business.setAddress(request.address());
        }
        if (request.latitude() != null && request.longitude() != null) {
            business.updateLocation(request.latitude(), request.longitude());
        }
        if (request.phone() != null) {
            business.setPhone(request.phone());
        }
        if (request.category() != null) {
            business.setCategory(request.category());
        }
        if (request.businessHours() != null) {
            List<BusinessHours> hours = request.businessHours().stream()
                    .map(h -> new BusinessHours(
                            id,
                            h.dayOfWeek(),
                            h.openTime() != null ? LocalTime.parse(h.openTime()) : null,
                            h.closeTime() != null ? LocalTime.parse(h.closeTime()) : null,
                            h.closed()
                    ))
                    .toList();
            business.setBusinessHours(hours);
        }

        Business saved = businessWritePort.save(business);
        cachePort.invalidateSearchCache(saved.getLatitude(), saved.getLongitude());
        cachePort.invalidateBusinessCache(id);
        return toDetailResponse(saved);
    }

    @Override
    public void delete(Long id, Long ownerId) {
        Business business = businessWritePort.findByIdForWrite(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));

        if (!business.isOwnedBy(ownerId)) {
            throw new BusinessOwnershipException();
        }

        businessWritePort.deleteById(id);
        cachePort.invalidateSearchCache(business.getLatitude(), business.getLongitude());
        cachePort.invalidateBusinessCache(id);
    }

    private BusinessDetailResponse toDetailResponse(Business business) {
        List<BusinessDetailResponse.BusinessHoursDto> hoursDtos =
                business.getBusinessHours() != null
                        ? business.getBusinessHours().stream()
                        .map(h -> new BusinessDetailResponse.BusinessHoursDto(
                                h.getId(), h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime(), h.isClosed()))
                        .toList()
                        : Collections.emptyList();

        List<BusinessDetailResponse.BusinessPhotoDto> photoDtos =
                business.getPhotos() != null
                        ? business.getPhotos().stream()
                        .map(p -> new BusinessDetailResponse.BusinessPhotoDto(
                                p.getId(), p.getPhotoUrl(), p.getDisplayOrder()))
                        .toList()
                        : Collections.emptyList();

        return new BusinessDetailResponse(
                business.getId(),
                business.getName(),
                business.getAddress(),
                business.getLatitude(),
                business.getLongitude(),
                business.getPhone(),
                business.getCategory(),
                business.getOwnerId(),
                hoursDtos,
                photoDtos,
                business.getCreatedAt(),
                business.getUpdatedAt()
        );
    }
}
