package com.proximity.config;

import com.proximity.adapter.in.web.BusinessController;
import com.proximity.adapter.in.web.GlobalExceptionHandler;
import com.proximity.adapter.in.web.JwtAuthenticationFilter;
import com.proximity.adapter.in.web.SearchController;
import com.proximity.adapter.out.auth.JwtAuthAdapter;
import com.proximity.application.port.in.BusinessUseCase;
import com.proximity.application.port.in.SearchUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SearchController.class, BusinessController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthAdapter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthAdapter jwtAuthAdapter;

    @MockitoBean
    private SearchUseCase searchUseCase;

    @MockitoBean
    private BusinessUseCase businessUseCase;

    @Test
    @DisplayName("GET /api/search → 토큰 없이 200 OK")
    void searchWithoutToken() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.978")
                        .param("radius", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/businesses → 토큰 없이 401 Unauthorized")
    void createWithoutToken() throws Exception {
        mockMvc.perform(post("/api/businesses")
                        .contentType("application/json")
                        .content("{\"name\":\"test\",\"address\":\"addr\",\"latitude\":37.5,\"longitude\":126.9}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/businesses → 유효한 토큰 → 401이 아닌 응답")
    void createWithValidToken() throws Exception {
        // given
        String token = jwtAuthAdapter.generateToken(1L);

        // when
        int responseStatus = mockMvc.perform(post("/api/businesses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"test\",\"address\":\"addr\",\"latitude\":37.5,\"longitude\":126.9}"))
                .andReturn().getResponse().getStatus();

        // then
        assertThat(responseStatus).isNotEqualTo(401);
    }

    @Test
    @DisplayName("POST /api/owners/signup → 토큰 없이 접근 가능 (401 아님)")
    void signupWithoutToken() throws Exception {
        // when
        int responseStatus = mockMvc.perform(post("/api/owners/signup")
                        .contentType("application/json")
                        .content("{\"email\":\"test@test.com\",\"password\":\"pass\",\"name\":\"test\"}"))
                .andReturn().getResponse().getStatus();

        // then
        assertThat(responseStatus).isNotEqualTo(401);
    }

    @Test
    @DisplayName("POST /api/owners/login → 토큰 없이 접근 가능 (401 아님)")
    void loginWithoutToken() throws Exception {
        // when
        int responseStatus = mockMvc.perform(post("/api/owners/login")
                        .contentType("application/json")
                        .content("{\"email\":\"test@test.com\",\"password\":\"pass\"}"))
                .andReturn().getResponse().getStatus();

        // then
        assertThat(responseStatus).isNotEqualTo(401);
    }

    @Test
    @DisplayName("PUT /api/businesses/{id}/update → 토큰 없이 401 Unauthorized")
    void updateWithoutToken() throws Exception {
        mockMvc.perform(put("/api/businesses/{id}/update", 1L)
                        .contentType("application/json")
                        .content("{\"name\":\"수정\",\"address\":\"주소\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/businesses/{id}/delete → 토큰 없이 401 Unauthorized")
    void deleteWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/businesses/{id}/delete", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/businesses/{id} → 토큰 없이 접근 가능 (401 아님)")
    void getBusinessDetailWithoutToken() throws Exception {
        // when
        int responseStatus = mockMvc.perform(get("/api/businesses/{id}", 1L))
                .andReturn().getResponse().getStatus();

        // then
        assertThat(responseStatus).isNotEqualTo(401);
    }
}
