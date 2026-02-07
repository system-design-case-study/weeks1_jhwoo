package com.proximity.application.dto;

public record OwnerTokenResponse(
        String token,
        Long ownerId,
        String name
) {}
