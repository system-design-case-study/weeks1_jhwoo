package com.proximity.application.port.out;

public interface AuthPort {

    Long extractOwnerId(String token);

    boolean validateToken(String token);
}
