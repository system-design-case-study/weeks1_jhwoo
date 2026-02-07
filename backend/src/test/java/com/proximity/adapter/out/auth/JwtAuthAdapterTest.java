package com.proximity.adapter.out.auth;

import com.proximity.application.port.out.AuthPort;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthAdapterTest {

    private static final String SECRET = "proximity-jwt-secret-key-for-development-must-be-at-least-256-bits-long";
    private static final long EXPIRATION_MS = 3600000;

    private JwtAuthAdapter jwtAuthAdapter;

    @BeforeEach
    void setUp() {
        jwtAuthAdapter = new JwtAuthAdapter(SECRET, EXPIRATION_MS);
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("ownerId로 토큰 생성 → extractOwnerId로 동일한 값 반환")
        void generateAndExtract() {
            // given
            Long ownerId = 42L;

            // when
            String token = jwtAuthAdapter.generateToken(ownerId);
            Long extracted = jwtAuthAdapter.extractOwnerId(token);

            // then
            assertThat(extracted).isEqualTo(ownerId);
        }

        @Test
        @DisplayName("생성된 토큰은 유효함")
        void generatedTokenIsValid() {
            // given
            String token = jwtAuthAdapter.generateToken(1L);

            // when
            boolean valid = jwtAuthAdapter.validateToken(token);

            // then
            assertThat(valid).isTrue();
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("만료된 토큰 → false")
        void expiredToken() {
            // given
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .subject("1")
                    .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                    .expiration(new Date(System.currentTimeMillis() - 3600000))
                    .signWith(key)
                    .compact();

            // when
            boolean valid = jwtAuthAdapter.validateToken(expiredToken);

            // then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("잘못된 서명 → false")
        void invalidSignature() {
            // given
            SecretKey wrongKey = Keys.hmacShaKeyFor(
                    "wrong-secret-key-that-is-also-at-least-256-bits-long-for-hmac-sha".getBytes(StandardCharsets.UTF_8));
            String badToken = Jwts.builder()
                    .subject("1")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(wrongKey)
                    .compact();

            // when
            boolean valid = jwtAuthAdapter.validateToken(badToken);

            // then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("잘못된 형식의 토큰 → false")
        void malformedToken() {
            // when
            boolean valid = jwtAuthAdapter.validateToken("not-a-jwt-token");

            // then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("null 토큰 → false")
        void nullToken() {
            // when
            boolean valid = jwtAuthAdapter.validateToken(null);

            // then
            assertThat(valid).isFalse();
        }
    }

    @Nested
    @DisplayName("extractOwnerId")
    class ExtractOwnerId {

        @Test
        @DisplayName("유효한 토큰에서 ownerId 추출")
        void extractFromValidToken() {
            // given
            String token = jwtAuthAdapter.generateToken(99L);

            // when
            Long ownerId = jwtAuthAdapter.extractOwnerId(token);

            // then
            assertThat(ownerId).isEqualTo(99L);
        }

        @Test
        @DisplayName("유효하지 않은 토큰 → 예외 발생")
        void extractFromInvalidToken() {
            // when & then
            assertThatThrownBy(() -> jwtAuthAdapter.extractOwnerId("invalid-token"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
