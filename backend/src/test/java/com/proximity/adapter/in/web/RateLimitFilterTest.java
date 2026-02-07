package com.proximity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.port.in.SearchUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SearchController.class, GlobalExceptionHandler.class})
@Import(RateLimitFilterTest.RateLimitTestConfig.class)
@DisplayName("Rate Limiting 테스트 (T-7.6)")
class RateLimitFilterTest {

    static class RateLimitTestConfig {
        @Bean
        public RateLimitFilter rateLimitFilter(ObjectMapper objectMapper) {
            return new RateLimitFilter(objectMapper);
        }

        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http,
                                                   RateLimitFilter rateLimitFilter) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @MockitoBean
    private SearchUseCase searchUseCase;

    @BeforeEach
    void setUp() {
        rateLimitFilter.clear();
    }

    @Test
    @DisplayName("IP당 분당 60회 초과 시 429 Too Many Requests + Retry-After 헤더")
    void rateLimitExceeded() throws Exception {
        // given
        given(searchUseCase.search(any()))
                .willReturn(new SearchResponse(List.of(), 0, 0, 20));

        // when - 60회 정상 요청
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/search")
                            .param("latitude", "37.5665")
                            .param("longitude", "126.9780")
                            .param("radius", "1"))
                    .andExpect(status().isOk());
        }

        // then - 61번째 요청 → 429
        mockMvc.perform(get("/api/search")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radius", "1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("제한 미만 요청은 정상 처리")
    void withinRateLimit() throws Exception {
        // given
        given(searchUseCase.search(any()))
                .willReturn(new SearchResponse(List.of(), 0, 0, 20));

        // when & then
        MvcResult result = mockMvc.perform(get("/api/search")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radius", "1"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
    }
}
