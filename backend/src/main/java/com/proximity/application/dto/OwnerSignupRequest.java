package com.proximity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerSignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 4) String password,
        @NotBlank String name
) {}
