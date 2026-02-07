package com.proximity.adapter.in.web;

import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessUpdateRequest;
import com.proximity.application.port.in.BusinessUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessUseCase businessUseCase;

    public BusinessController(BusinessUseCase businessUseCase) {
        this.businessUseCase = businessUseCase;
    }

    @PostMapping
    public ResponseEntity<BusinessDetailResponse> create(
            @Valid @RequestBody BusinessCreateRequest request,
            @RequestHeader("X-Owner-Id") Long ownerId) {
        BusinessDetailResponse response = businessUseCase.create(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/update")
    public ResponseEntity<BusinessDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody BusinessUpdateRequest request,
            @RequestHeader("X-Owner-Id") Long ownerId) {
        BusinessDetailResponse response = businessUseCase.update(id, request, ownerId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Owner-Id") Long ownerId) {
        businessUseCase.delete(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}
