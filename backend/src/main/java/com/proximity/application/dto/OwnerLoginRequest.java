package com.proximity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OwnerLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
