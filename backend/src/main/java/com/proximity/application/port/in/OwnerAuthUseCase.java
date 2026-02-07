package com.proximity.application.port.in;

import com.proximity.application.dto.OwnerLoginRequest;
import com.proximity.application.dto.OwnerSignupRequest;
import com.proximity.application.dto.OwnerTokenResponse;

public interface OwnerAuthUseCase {

    OwnerTokenResponse signup(OwnerSignupRequest request);

    OwnerTokenResponse login(OwnerLoginRequest request);
}
