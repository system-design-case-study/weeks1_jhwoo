package com.proximity.application.service;

import com.proximity.adapter.out.auth.JwtAuthAdapter;
import com.proximity.application.dto.OwnerLoginRequest;
import com.proximity.application.dto.OwnerSignupRequest;
import com.proximity.application.dto.OwnerTokenResponse;
import com.proximity.application.exception.DuplicateEmailException;
import com.proximity.application.exception.InvalidCredentialsException;
import com.proximity.application.port.out.OwnerPort;
import com.proximity.domain.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OwnerAuthServiceTest {

    @Mock
    private OwnerPort ownerPort;

    private PasswordEncoder passwordEncoder;
    private JwtAuthAdapter jwtAuthAdapter;
    private OwnerAuthService ownerAuthService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtAuthAdapter = new JwtAuthAdapter(
                "proximity-jwt-secret-key-for-development-must-be-at-least-256-bits-long",
                3600000);
        ownerAuthService = new OwnerAuthService(ownerPort, passwordEncoder, jwtAuthAdapter);
    }

    @Nested
    @DisplayName("signup")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 → JWT 토큰 반환")
        void signupSuccess() {
            // given
            OwnerSignupRequest request = new OwnerSignupRequest("test@test.com", "password123", "테스트");
            given(ownerPort.existsByEmail("test@test.com")).willReturn(false);
            given(ownerPort.save(any(Owner.class))).willAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                owner.setId(1L);
                return owner;
            });

            // when
            OwnerTokenResponse response = ownerAuthService.signup(request);

            // then
            assertThat(response.ownerId()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("테스트");
            assertThat(response.token()).isNotBlank();
            assertThat(jwtAuthAdapter.extractOwnerId(response.token())).isEqualTo(1L);
        }

        @Test
        @DisplayName("중복 이메일 → DuplicateEmailException")
        void signupDuplicateEmail() {
            // given
            OwnerSignupRequest request = new OwnerSignupRequest("dup@test.com", "password123", "테스트");
            given(ownerPort.existsByEmail("dup@test.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> ownerAuthService.signup(request))
                    .isInstanceOf(DuplicateEmailException.class);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상 로그인 → JWT 토큰 반환")
        void loginSuccess() {
            // given
            String rawPassword = "password123";
            String encoded = passwordEncoder.encode(rawPassword);
            Owner owner = new Owner("test@test.com", encoded, "테스트");
            owner.setId(1L);
            given(ownerPort.findByEmail("test@test.com")).willReturn(Optional.of(owner));

            OwnerLoginRequest request = new OwnerLoginRequest("test@test.com", rawPassword);

            // when
            OwnerTokenResponse response = ownerAuthService.login(request);

            // then
            assertThat(response.ownerId()).isEqualTo(1L);
            assertThat(response.token()).isNotBlank();
        }

        @Test
        @DisplayName("존재하지 않는 이메일 → InvalidCredentialsException")
        void loginEmailNotFound() {
            // given
            given(ownerPort.findByEmail("unknown@test.com")).willReturn(Optional.empty());
            OwnerLoginRequest request = new OwnerLoginRequest("unknown@test.com", "password123");

            // when & then
            assertThatThrownBy(() -> ownerAuthService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("잘못된 비밀번호 → InvalidCredentialsException")
        void loginWrongPassword() {
            // given
            String encoded = passwordEncoder.encode("correct-password");
            Owner owner = new Owner("test@test.com", encoded, "테스트");
            owner.setId(1L);
            given(ownerPort.findByEmail("test@test.com")).willReturn(Optional.of(owner));

            OwnerLoginRequest request = new OwnerLoginRequest("test@test.com", "wrong-password");

            // when & then
            assertThatThrownBy(() -> ownerAuthService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }
}
