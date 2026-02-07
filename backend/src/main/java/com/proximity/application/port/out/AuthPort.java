package com.proximity.application.port.out;

public interface AuthPort {

    String generateToken(Long ownerId);

    Long extractOwnerId(String token);

    boolean validateToken(String token);
}
