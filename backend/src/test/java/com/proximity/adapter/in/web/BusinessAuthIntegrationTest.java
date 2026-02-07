package com.proximity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximity.adapter.out.auth.JwtAuthAdapter;
import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessUpdateRequest;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.port.in.BusinessUseCase;
import com.proximity.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BusinessController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthAdapter.class})
class BusinessAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtAuthAdapter jwtAuthAdapter;

    @MockitoBean
    private BusinessUseCase businessUseCase;

    private static final Long OWNER_A_ID = 1L;
    private static final Long OWNER_B_ID = 2L;
    private static final Long BUSINESS_ID = 100L;

    private BusinessDetailResponse testResponse() {
        return new BusinessDetailResponse(
                BUSINESS_ID, "테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                "02-1234-5678", "카페", OWNER_A_ID,
                Collections.emptyList(), Collections.emptyList(), null, null);
    }

    @Nested
    @DisplayName("인증 필요 경로")
    class AuthRequired {

        @Test
        @DisplayName("POST /api/businesses → JWT 토큰 없이 401 (FR-006)")
        void createWithoutToken() throws Exception {
            mockMvc.perform(post("/api/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"test\",\"address\":\"addr\",\"latitude\":37.5,\"longitude\":126.9}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/businesses/{id}/update → JWT 토큰 없이 401")
        void updateWithoutToken() throws Exception {
            mockMvc.perform(put("/api/businesses/{id}/update", BUSINESS_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"update\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/businesses/{id}/delete → JWT 토큰 없이 401")
        void deleteWithoutToken() throws Exception {
            mockMvc.perform(delete("/api/businesses/{id}/delete", BUSINESS_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("인증 불필요 경로")
    class NoAuthRequired {

        @Test
        @DisplayName("GET /api/businesses/{id} → 토큰 없이 200 OK")
        void getDetailWithoutToken() throws Exception {
            // given
            given(businessUseCase.getDetail(BUSINESS_ID)).willReturn(testResponse());

            // when & then
            mockMvc.perform(get("/api/businesses/{id}", BUSINESS_ID))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("JWT 인증 후 CRUD")
    class AuthenticatedCrud {

        @Test
        @DisplayName("JWT 토큰으로 사업장 생성 → 201 Created (FR-006)")
        void createWithJwt() throws Exception {
            // given
            String token = jwtAuthAdapter.generateToken(OWNER_A_ID);
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "새 카페", "서울시 강남구", 37.5665, 126.9780, "02-1234-5678", "카페", null);
            given(businessUseCase.create(any(BusinessCreateRequest.class), eq(OWNER_A_ID)))
                    .willReturn(testResponse());

            // when & then
            mockMvc.perform(post("/api/businesses")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("테스트 카페"));
        }

        @Test
        @DisplayName("Owner A가 생성한 사업장 → Owner B가 수정 시도 → 403 Forbidden (FR-009)")
        void updateByWrongOwner() throws Exception {
            // given
            String ownerBToken = jwtAuthAdapter.generateToken(OWNER_B_ID);
            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정", null, null, null, null, null, null);
            given(businessUseCase.update(eq(BUSINESS_ID), any(BusinessUpdateRequest.class), eq(OWNER_B_ID)))
                    .willThrow(new BusinessOwnershipException());

            // when & then
            mockMvc.perform(put("/api/businesses/{id}/update", BUSINESS_ID)
                            .header("Authorization", "Bearer " + ownerBToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Owner A가 수정 → 200 OK (FR-009)")
        void updateByCorrectOwner() throws Exception {
            // given
            String ownerAToken = jwtAuthAdapter.generateToken(OWNER_A_ID);
            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정된 카페", null, null, null, null, null, null);
            given(businessUseCase.update(eq(BUSINESS_ID), any(BusinessUpdateRequest.class), eq(OWNER_A_ID)))
                    .willReturn(testResponse());

            // when & then
            mockMvc.perform(put("/api/businesses/{id}/update", BUSINESS_ID)
                            .header("Authorization", "Bearer " + ownerAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }
}
