package com.proximity.application.service;

import com.proximity.application.dto.OwnerLoginRequest;
import com.proximity.application.dto.OwnerSignupRequest;
import com.proximity.application.dto.OwnerTokenResponse;
import com.proximity.application.exception.DuplicateEmailException;
import com.proximity.application.exception.InvalidCredentialsException;
import com.proximity.application.port.in.OwnerAuthUseCase;
import com.proximity.application.port.out.AuthPort;
import com.proximity.application.port.out.OwnerPort;
import com.proximity.domain.Owner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OwnerAuthService implements OwnerAuthUseCase {

    private final OwnerPort ownerPort;
    private final PasswordEncoder passwordEncoder;
    private final AuthPort authPort;

    public OwnerAuthService(OwnerPort ownerPort,
                            PasswordEncoder passwordEncoder,
                            AuthPort authPort) {
        this.ownerPort = ownerPort;
        this.passwordEncoder = passwordEncoder;
        this.authPort = authPort;
    }

    @Override
    public OwnerTokenResponse signup(OwnerSignupRequest request) {
        if (ownerPort.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Owner owner = new Owner(request.email(), encodedPassword, request.name());
        Owner saved = ownerPort.save(owner);

        String token = authPort.generateToken(saved.getId());
        return new OwnerTokenResponse(token, saved.getId(), saved.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerTokenResponse login(OwnerLoginRequest request) {
        Owner owner = ownerPort.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), owner.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = authPort.generateToken(owner.getId());
        return new OwnerTokenResponse(token, owner.getId(), owner.getName());
    }
}
