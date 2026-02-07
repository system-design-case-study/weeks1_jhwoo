package com.proximity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessUpdateRequest;
import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.port.in.BusinessUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BusinessController.class, GlobalExceptionHandler.class})
@Import(BusinessControllerTest.TestSecurityConfig.class)
class BusinessControllerTest {

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
    private BusinessUseCase businessUseCase;

    private static final Long OWNER_ID = 1L;
    private static final Long BUSINESS_ID = 100L;

    private BusinessDetailResponse testResponse() {
        return new BusinessDetailResponse(
                BUSINESS_ID, "테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                "02-1234-5678", "카페", OWNER_ID,
                Collections.emptyList(), Collections.emptyList(), null, null);
    }

    @Nested
    @DisplayName("GET /api/businesses/{id}")
    class GetBusinessDetail {

        @Test
        @DisplayName("존재하는 사업장 → 200 OK (FR-005)")
        void getDetailSuccess() throws Exception {
            // given
            given(businessUseCase.getDetail(BUSINESS_ID)).willReturn(testResponse());

            // when & then
            mockMvc.perform(get("/api/businesses/{id}", BUSINESS_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(BUSINESS_ID))
                    .andExpect(jsonPath("$.name").value("테스트 카페"))
                    .andExpect(jsonPath("$.address").value("서울시 강남구"))
                    .andExpect(jsonPath("$.latitude").value(37.5665))
                    .andExpect(jsonPath("$.longitude").value(126.9780));
        }

        @Test
        @DisplayName("존재하지 않는 사업장 → 404 Not Found (EC-5)")
        void getDetailNotFound() throws Exception {
            // given
            given(businessUseCase.getDetail(99999L))
                    .willThrow(new BusinessNotFoundException(99999L));

            // when & then
            mockMvc.perform(get("/api/businesses/{id}", 99999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /api/businesses")
    class CreateBusiness {

        @Test
        @DisplayName("유효한 요청 → 201 Created")
        void createSuccess() throws Exception {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "새 카페", "서울시 강남구", 37.5665, 126.9780,
                    "02-1234-5678", "카페", null);

            given(businessUseCase.create(any(BusinessCreateRequest.class), eq(OWNER_ID)))
                    .willReturn(testResponse());

            // when & then
            mockMvc.perform(post("/api/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Owner-Id", OWNER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("테스트 카페"));
        }

        @Test
        @DisplayName("좌표 범위 초과 → 400 Bad Request (EC-2)")
        void createInvalidCoordinates() throws Exception {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "카페", "주소", 999.0, 126.9780, null, null, null);

            // when & then
            mockMvc.perform(post("/api/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Owner-Id", OWNER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이름 미입력 → 400 Bad Request")
        void createMissingName() throws Exception {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "", "주소", 37.5665, 126.9780, null, null, null);

            // when & then
            mockMvc.perform(post("/api/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Owner-Id", OWNER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("중복 사업장 → 409 Conflict (EC-3)")
        void createDuplicate() throws Exception {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "기존 카페", "서울시 강남구", 37.5665, 126.9780,
                    null, null, null);

            given(businessUseCase.create(any(BusinessCreateRequest.class), eq(OWNER_ID)))
                    .willThrow(new DuplicateBusinessException());

            // when & then
            mockMvc.perform(post("/api/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Owner-Id", OWNER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("PUT /api/businesses/{id}/update")
    class UpdateBusiness {

        @Test
        @DisplayName("정상 수정 → 200 OK")
        void updateSuccess() throws Exception {
            // given
            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정된 카페", null, null, null, null, null, null);

            given(businessUseCase.update(eq(BUSINESS_ID), any(BusinessUpdateRequest.class), eq(OWNER_ID)))
                    .willReturn(testResponse());

            // when & then
            mockMvc.perform(put("/api/businesses/{id}/update", BUSINESS_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Owner-Id", OWNER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("소유주 불일치 → 403 Forbidden (FR-009)")
        void updateOwnerMismatch() throws Exception {
            // given
            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정", null, null, null, null, null, null);

            given(businessUseCase.update(eq(BUSINESS_ID), any(BusinessUpdateRequest.class), eq(OWNER_ID)))
                    .willThrow(new BusinessOwnershipException());

            // when & then
            mockMvc.perform(put("/api/businesses/{id}/update", BUSINESS_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Owner-Id", OWNER_ID)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/businesses/{id}/delete")
    class DeleteBusiness {

        @Test
        @DisplayName("정상 삭제 → 204 No Content")
        void deleteSuccess() throws Exception {
            // given
            willDoNothing().given(businessUseCase).delete(BUSINESS_ID, OWNER_ID);

            // when & then
            mockMvc.perform(delete("/api/businesses/{id}/delete", BUSINESS_ID)
                            .header("X-Owner-Id", OWNER_ID))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("존재하지 않는 사업장 → 404 Not Found")
        void deleteNotFound() throws Exception {
            // given
            willThrow(new BusinessNotFoundException(BUSINESS_ID))
                    .given(businessUseCase).delete(BUSINESS_ID, OWNER_ID);

            // when & then
            mockMvc.perform(delete("/api/businesses/{id}/delete", BUSINESS_ID)
                            .header("X-Owner-Id", OWNER_ID))
                    .andExpect(status().isNotFound());
        }
    }
}
