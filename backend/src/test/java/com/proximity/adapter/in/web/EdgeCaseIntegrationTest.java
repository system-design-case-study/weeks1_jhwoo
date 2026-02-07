package com.proximity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.exception.InvalidRadiusException;
import com.proximity.application.port.in.BusinessUseCase;
import com.proximity.application.port.in.SearchUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SearchController.class, BusinessController.class, GlobalExceptionHandler.class})
@Import(EdgeCaseIntegrationTest.TestSecurityConfig.class)
@DisplayName("Edge Case 통합 테스트")
class EdgeCaseIntegrationTest {

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
    private SearchUseCase searchUseCase;

    @MockitoBean
    private BusinessUseCase businessUseCase;

    private static final Long OWNER_ID = 1L;

    private UsernamePasswordAuthenticationToken ownerAuth() {
        return new UsernamePasswordAuthenticationToken(OWNER_ID, null, Collections.emptyList());
    }

    @Nested
    @DisplayName("EC-1: 빈 결과 처리 (T-7.2)")
    class EmptyResult {

        @Test
        @DisplayName("사업장 없는 좌표로 검색 → 200 OK + {businesses: [], total: 0}")
        void emptySearchResult() throws Exception {
            // given
            given(searchUseCase.search(any()))
                    .willReturn(new SearchResponse(List.of(), 0, 0, 20));

            // when & then
            mockMvc.perform(get("/api/search")
                            .param("latitude", "37.5665")
                            .param("longitude", "126.9780")
                            .param("radius", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businesses").isArray())
                    .andExpect(jsonPath("$.businesses").isEmpty())
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }
    }

    @Nested
    @DisplayName("EC-2: 유효하지 않은 좌표 (T-7.3)")
    class InvalidCoordinates {

        @Test
        @DisplayName("latitude=999 → 400 Bad Request")
        void latitudeOutOfRange() throws Exception {
            mockMvc.perform(get("/api/search")
                            .param("latitude", "999")
                            .param("longitude", "126.9780")
                            .param("radius", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("latitude 미입력 → 400 Bad Request")
        void latitudeNull() throws Exception {
            mockMvc.perform(get("/api/search")
                            .param("longitude", "126.9780")
                            .param("radius", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("longitude=999 → 400 Bad Request")
        void longitudeOutOfRange() throws Exception {
            mockMvc.perform(get("/api/search")
                            .param("latitude", "37.5665")
                            .param("longitude", "999")
                            .param("radius", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("radius=3 (비허용 값) → 400 Bad Request")
        void invalidRadius() throws Exception {
            // given
            given(searchUseCase.search(any()))
                    .willThrow(new InvalidRadiusException(3.0));

            // when & then
            mockMvc.perform(get("/api/search")
                            .param("latitude", "37.5665")
                            .param("longitude", "126.9780")
                            .param("radius", "3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RADIUS"));
        }
    }

    @Nested
    @DisplayName("EC-3: 중복 사업장 등록 (T-7.4)")
    class DuplicateBusiness {

        @Test
        @DisplayName("첫 번째 등록 → 201, 두 번째 동일 등록 → 409")
        void duplicateRegistration() throws Exception {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "테스트 카페", "서울시 강남구", 37.5665, 126.9780, null, "카페", null);

            BusinessDetailResponse response = new BusinessDetailResponse(
                    1L, "테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                    null, "카페", OWNER_ID,
                    Collections.emptyList(), Collections.emptyList(), null, null);

            given(businessUseCase.create(any(), eq(OWNER_ID)))
                    .willReturn(response)
                    .willThrow(new DuplicateBusinessException());

            // when - 첫 번째 등록
            mockMvc.perform(post("/api/businesses")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // when - 두 번째 동일 등록
            mockMvc.perform(post("/api/businesses")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS"));
        }
    }

    @Nested
    @DisplayName("EC-5: 삭제 후 상세 조회 (T-7.5)")
    class DeletedBusinessDetail {

        @Test
        @DisplayName("삭제된 사업장 상세 조회 → 404 Not Found")
        void deletedBusinessReturns404() throws Exception {
            // given
            given(businessUseCase.getDetail(100L))
                    .willThrow(new BusinessNotFoundException(100L));

            // when & then
            mockMvc.perform(get("/api/businesses/{id}", 100L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("존재하지 않거나 삭제된 사업장입니다"));
        }
    }
}
