package com.proximity.adapter.in.web;

import com.proximity.application.dto.OwnerLoginRequest;
import com.proximity.application.dto.OwnerSignupRequest;
import com.proximity.application.dto.OwnerTokenResponse;
import com.proximity.application.port.in.OwnerAuthUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owners")
public class OwnerController {

    private final OwnerAuthUseCase ownerAuthUseCase;

    public OwnerController(OwnerAuthUseCase ownerAuthUseCase) {
        this.ownerAuthUseCase = ownerAuthUseCase;
    }

    @PostMapping("/signup")
    public ResponseEntity<OwnerTokenResponse> signup(@Valid @RequestBody OwnerSignupRequest request) {
        OwnerTokenResponse response = ownerAuthUseCase.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<OwnerTokenResponse> login(@Valid @RequestBody OwnerLoginRequest request) {
        OwnerTokenResponse response = ownerAuthUseCase.login(request);
        return ResponseEntity.ok(response);
    }
}
