package com.proximity.adapter.in.web;

import com.proximity.application.dto.BusinessSummary;
import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.exception.InvalidRadiusException;
import com.proximity.application.port.in.SearchUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import(SearchControllerTest.TestConfig.class)
class SearchControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SearchUseCase searchUseCase() {
            return mock(SearchUseCase.class);
        }

        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchUseCase searchUseCase;

    @Test
    @DisplayName("GET /api/search - 정상 검색 시 200 OK를 반환한다 (FR-001, FR-003)")
    void search_validParams_returns200() throws Exception {
        // given
        List<BusinessSummary> businesses = List.of(
                new BusinessSummary(1L, "카페A", "서울시 중구", 37.5665, 126.9780, 150.0, "카페")
        );
        SearchResponse response = new SearchResponse(businesses, 1, 0, 20);
        when(searchUseCase.search(any(SearchRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/search")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radius", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businesses").isArray())
                .andExpect(jsonPath("$.businesses[0].name").value("카페A"))
                .andExpect(jsonPath("$.businesses[0].distance").value(150.0))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("GET /api/search - 좌표 범위 초과 시 400 Bad Request를 반환한다 (EC-2)")
    void search_invalidLatitude_returns400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/search")
                        .param("latitude", "999")
                        .param("longitude", "126.9780")
                        .param("radius", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("GET /api/search - latitude 누락 시 400 Bad Request를 반환한다 (EC-2)")
    void search_missingLatitude_returns400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/search")
                        .param("longitude", "126.9780")
                        .param("radius", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
