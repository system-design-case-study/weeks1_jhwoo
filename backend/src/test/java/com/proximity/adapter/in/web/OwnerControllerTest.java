package com.proximity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximity.application.dto.OwnerLoginRequest;
import com.proximity.application.dto.OwnerSignupRequest;
import com.proximity.application.dto.OwnerTokenResponse;
import com.proximity.application.exception.DuplicateEmailException;
import com.proximity.application.exception.InvalidCredentialsException;
import com.proximity.application.port.in.OwnerAuthUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OwnerController.class, GlobalExceptionHandler.class})
@Import(OwnerControllerTest.TestSecurityConfig.class)
class OwnerControllerTest {

    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OwnerAuthUseCase ownerAuthUseCase;


    @Nested
    @DisplayName("POST /api/owners/signup")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 → 201 Created + 토큰 반환")
        void signupSuccess() throws Exception {
            // given
            OwnerSignupRequest request = new OwnerSignupRequest("test@test.com", "password123", "테스트");
            OwnerTokenResponse response = new OwnerTokenResponse("jwt-token", 1L, "테스트");
            given(ownerAuthUseCase.signup(any(OwnerSignupRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/owners/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").value("jwt-token"))
                    .andExpect(jsonPath("$.ownerId").value(1))
                    .andExpect(jsonPath("$.name").value("테스트"));
        }

        @Test
        @DisplayName("중복 이메일 → 409 Conflict")
        void signupDuplicate() throws Exception {
            // given
            OwnerSignupRequest request = new OwnerSignupRequest("dup@test.com", "password123", "테스트");
            given(ownerAuthUseCase.signup(any(OwnerSignupRequest.class)))
                    .willThrow(new DuplicateEmailException("dup@test.com"));

            // when & then
            mockMvc.perform(post("/api/owners/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        }

        @Test
        @DisplayName("이메일 미입력 → 400 Bad Request")
        void signupInvalidEmail() throws Exception {
            // given
            OwnerSignupRequest request = new OwnerSignupRequest("", "password123", "테스트");

            // when & then
            mockMvc.perform(post("/api/owners/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/owners/login")
    class Login {

        @Test
        @DisplayName("정상 로그인 → 200 OK + 토큰 반환")
        void loginSuccess() throws Exception {
            // given
            OwnerLoginRequest request = new OwnerLoginRequest("test@test.com", "password123");
            OwnerTokenResponse response = new OwnerTokenResponse("jwt-token", 1L, "테스트");
            given(ownerAuthUseCase.login(any(OwnerLoginRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/owners/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token"))
                    .andExpect(jsonPath("$.ownerId").value(1));
        }

        @Test
        @DisplayName("잘못된 인증 정보 → 401 Unauthorized")
        void loginFailed() throws Exception {
            // given
            OwnerLoginRequest request = new OwnerLoginRequest("test@test.com", "wrong");
            given(ownerAuthUseCase.login(any(OwnerLoginRequest.class)))
                    .willThrow(new InvalidCredentialsException());

            // when & then
            mockMvc.perform(post("/api/owners/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }
}
