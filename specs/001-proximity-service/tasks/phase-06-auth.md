# Phase 06: 인증/인가 — JWT + Owner 권한 검증

**Status**: pending
**Prerequisites**: Phase 03 완료
**Covers**: plan.md (AuthPort, SecurityConfig), spec.md (FR-006, FR-009), api.yaml (bearerAuth)

## 목표
JWT 기반 인증과 사업장 소유주 권한 검증을 구현한다.

## Tasks

### 6.1 SecurityConfig 설정
- `config/SecurityConfig.java`
  - Spring Security 설정
  - 인증 불필요 경로: `GET /api/search`, `GET /api/search/geohash`, `GET /api/businesses/{id}`
  - 인증 필요 경로: `POST/PUT/DELETE /api/businesses/**`
  - JWT 필터 등록

### 6.2 JwtAuthAdapter 구현
- `adapter/out/auth/JwtAuthAdapter.java` implements `AuthPort`
  - `extractOwnerId(token)`: JWT 토큰에서 ownerId 추출
  - `validateToken(token)`: 토큰 유효성 검증 (만료, 서명)
- JWT 시크릿 키: `application.yml`에서 설정

### 6.3 JWT 필터
- `adapter/in/web/JwtAuthenticationFilter.java`
  - `Authorization: Bearer <token>` 헤더에서 토큰 추출
  - 토큰 유효 → SecurityContext에 인증 정보 설정
  - 토큰 무효/미존재 → 인증 필요 경로면 401

### 6.4 Owner 등록/로그인 (보조)
- Owner 등록: `POST /api/owners/signup` (email, password, name)
- Owner 로그인: `POST /api/owners/login` (email, password) → JWT 토큰 반환
- 비밀번호: BCrypt 해시

### 6.5 BusinessController 인증 통합
- CUD 엔드포인트에서 JWT 토큰으로 ownerId 추출
- BusinessService에 ownerId 전달 → 소유권 검증 (FR-009)

## TDD 접근

### 테스트 우선 작성
- **T-6.1**: JWT 토큰 생성/검증 단위 테스트
  - 토큰 생성 → extractOwnerId → 동일한 ownerId 반환
  - 만료된 토큰 → validateToken → false
  - 잘못된 서명 → validateToken → false
- **T-6.2**: JwtAuthenticationFilter 테스트
  - 유효한 Bearer 토큰 → SecurityContext 인증 설정
  - 토큰 없음 → 인증 미설정
- **T-6.3**: SecurityConfig 통합 테스트 (@WebMvcTest)
  - GET /api/search → 토큰 없이 200 OK
  - POST /api/businesses → 토큰 없이 401
  - POST /api/businesses → 유효한 토큰 → 정상 처리
- **T-6.4**: Owner 등록/로그인 테스트
  - 등록 → 로그인 → JWT 토큰 반환 확인
  - 잘못된 비밀번호 → 401
- **T-6.5**: 소유권 검증 E2E 테스트
  - Owner A가 생성한 사업장 → Owner B가 수정 시도 → 403 (FR-009)
  - Owner A가 수정 → 200 OK

## 완료 기준
- [ ] JWT 토큰 생성/검증 테스트 통과
- [ ] SecurityConfig 경로별 인증 테스트 통과
- [ ] Owner 등록/로그인 기능 동작
- [ ] 소유권 검증 테스트 통과 (FR-009)
- [ ] 인증 미필요 API (검색, 상세 조회)는 토큰 없이 접근 가능

## 관련 Spec 항목
- spec.md FR-006 (인증된 소유주의 CRUD)
- spec.md FR-009 (소유주 아닌 사용자 수정/삭제 거부)
- spec.md User Story 3 Acceptance Scenario 4 (권한 없음 오류)
- api.yaml: bearerAuth security scheme
- plan.md (AuthPort → JwtAdapter)
