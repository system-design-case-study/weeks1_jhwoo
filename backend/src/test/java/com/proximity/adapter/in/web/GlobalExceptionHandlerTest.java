package com.proximity.adapter.in.web;

import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.exception.DuplicateEmailException;
import com.proximity.application.exception.InvalidCredentialsException;
import com.proximity.application.exception.InvalidRadiusException;
import com.proximity.application.port.in.BusinessUseCase;
import com.proximity.application.port.in.OwnerAuthUseCase;
import com.proximity.application.port.in.SearchUseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
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

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BusinessController.class, OwnerController.class, SearchController.class, GlobalExceptionHandler.class})
@Import(GlobalExceptionHandlerTest.TestSecurityConfig.class)
@DisplayName("GlobalExceptionHandler 단위 테스트 (T-7.1)")
class GlobalExceptionHandlerTest {

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

    @MockitoBean
    private BusinessUseCase businessUseCase;

    @MockitoBean
    private OwnerAuthUseCase ownerAuthUseCase;

    @MockitoBean
    private SearchUseCase searchUseCase;

    private static final Long OWNER_ID = 1L;
    private static final Long BUSINESS_ID = 100L;

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken ownerAuth() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                OWNER_ID, null, Collections.emptyList());
    }

    @Test
    @DisplayName("BusinessNotFoundException → 404 + {code: BUSINESS_NOT_FOUND}")
    void businessNotFound() throws Exception {
        // given
        given(businessUseCase.getDetail(99999L))
                .willThrow(new BusinessNotFoundException(99999L));

        // when & then
        mockMvc.perform(get("/api/businesses/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 사업장입니다"));
    }

    @Test
    @DisplayName("DuplicateBusinessException → 409 + {code: DUPLICATE_BUSINESS}")
    void duplicateBusiness() throws Exception {
        // given
        given(businessUseCase.create(any(), eq(OWNER_ID)))
                .willThrow(new DuplicateBusinessException());

        // when & then
        mockMvc.perform(post("/api/businesses")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"카페\",\"address\":\"주소\",\"latitude\":37.5,\"longitude\":126.9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS"))
                .andExpect(jsonPath("$.message").value("동일한 이름과 위치의 사업장이 이미 등록되어 있습니다"));
    }

    @Test
    @DisplayName("BusinessOwnershipException → 403 + {code: FORBIDDEN}")
    void ownershipForbidden() throws Exception {
        // given
        willThrow(new BusinessOwnershipException())
                .given(businessUseCase).delete(BUSINESS_ID, OWNER_ID);

        // when & then
        mockMvc.perform(delete("/api/businesses/{id}/delete", BUSINESS_ID)
                        .with(authentication(ownerAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("해당 사업장의 소유주가 아닙니다"));
    }

    @Test
    @DisplayName("DuplicateEmailException → 409 + {code: DUPLICATE_EMAIL}")
    void duplicateEmail() throws Exception {
        // given
        given(ownerAuthUseCase.signup(any()))
                .willThrow(new DuplicateEmailException("test@test.com"));

        // when & then
        mockMvc.perform(post("/api/owners/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"pass1234\",\"name\":\"테스트\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 등록된 이메일입니다: test@test.com"));
    }

    @Test
    @DisplayName("InvalidCredentialsException → 401 + {code: INVALID_CREDENTIALS}")
    void invalidCredentials() throws Exception {
        // given
        given(ownerAuthUseCase.login(any()))
                .willThrow(new InvalidCredentialsException());

        // when & then
        mockMvc.perform(post("/api/owners/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 + {code: INVALID_PARAMETER} + 필드별 메시지")
    void validationError() throws Exception {
        // when & then
        mockMvc.perform(post("/api/businesses")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"address\":\"\",\"latitude\":null,\"longitude\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("좌표 범위 초과 → 400 + {code: INVALID_PARAMETER} (EC-2)")
    void invalidCoordinates() throws Exception {
        // when & then
        mockMvc.perform(post("/api/businesses")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"카페\",\"address\":\"주소\",\"latitude\":999.0,\"longitude\":126.9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("InvalidRadiusException → 400 + {code: INVALID_RADIUS}")
    void invalidRadius() throws Exception {
        // given
        given(searchUseCase.search(any()))
                .willThrow(new InvalidRadiusException(3.0));

        // when & then
        mockMvc.perform(get("/api/search")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.978")
                        .param("radius", "3.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RADIUS"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409 + {code: DATA_INTEGRITY_VIOLATION}")
    void dataIntegrityViolation() throws Exception {
        // given
        given(businessUseCase.create(any(), eq(OWNER_ID)))
                .willThrow(new DataIntegrityViolationException("constraint violation"));

        // when & then
        mockMvc.perform(post("/api/businesses")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"카페\",\"address\":\"주소\",\"latitude\":37.5,\"longitude\":126.9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("데이터 무결성 제약 조건 위반입니다."));
    }

    @Test
    @DisplayName("예상치 못한 Exception → 500 + {code: INTERNAL_ERROR}")
    void unexpectedException() throws Exception {
        // given
        given(businessUseCase.getDetail(BUSINESS_ID))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        // when & then
        mockMvc.perform(get("/api/businesses/{id}", BUSINESS_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
