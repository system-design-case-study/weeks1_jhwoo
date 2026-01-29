# Phase 07: Edge Cases + Validation + Error Handling

**Status**: pending
**Prerequisites**: Phase 04, 05, 06 완료
**Covers**: spec.md (Edge Cases EC-1~EC-5), api.yaml (ErrorResponse)

## 목표
모든 Edge Case 시나리오에 대한 통합 처리와 글로벌 에러 핸들링을 구현한다.

## Tasks

### 7.1 글로벌 예외 핸들러
- `adapter/in/web/GlobalExceptionHandler.java` (`@RestControllerAdvice`)
  - `MethodArgumentNotValidException` → 400 (EC-2)
  - `BusinessNotFoundException` → 404 (EC-5)
  - `DuplicateBusinessException` → 409 (EC-3)
  - `UnauthorizedException` → 401
  - `ForbiddenException` → 403 (FR-009)
  - 응답 포맷: `{ "code": "ERROR_CODE", "message": "한국어 메시지" }`

### 7.2 커스텀 예외 클래스
- `domain/exception/BusinessNotFoundException.java`: "존재하지 않거나 삭제된 사업장입니다"
- `domain/exception/DuplicateBusinessException.java`: "동일한 이름과 위치의 사업장이 이미 등록되어 있습니다"
- `domain/exception/ForbiddenException.java`: "해당 사업장의 소유주가 아닙니다"

### 7.3 EC-1 빈 결과 처리 검증
- SearchService: 빈 결과 → `SearchResponse(businesses=[], total=0, page=0, size=20)` + 200 OK
- 프론트엔드에서 표시할 메시지: "주변에 등록된 사업장이 없습니다. 반경을 넓혀보세요."

### 7.4 EC-2 좌표 검증 계층 완성
- Controller 계층: `@Valid` Bean Validation
  - latitude: `@NotNull @DecimalMin("-90") @DecimalMax("90")`
  - longitude: `@NotNull @DecimalMin("-180") @DecimalMax("180")`
  - radius: `@NotNull` + 허용값 검증 (0.5, 1, 2, 5, 20)
- DB 계층: CHECK 제약 조건 + Trigger (이미 Phase 01에서 생성)

### 7.5 EC-4 Rate Limiting
- Nginx 레벨 Rate Limiting 설정 (docker-compose nginx.conf)
  - IP당 분당 60회 제한
  - 초과 시 429 Too Many Requests + Retry-After 헤더
- Spring Boot 레벨 보조: Bucket4j 또는 수동 구현 (선택)

### 7.6 EC-5 삭제 후 상세 조회 처리 통합 검증
- 시나리오: 사업장 삭제 → 검색 캐시에 잔존 → 상세 조회 시 404
- BusinessService.getDetail()에서 존재 여부 확인 → 404 반환 확인

## TDD 접근

### 테스트 우선 작성
- **T-7.1**: GlobalExceptionHandler 단위 테스트
  - BusinessNotFoundException → 404 + `{"code":"BUSINESS_NOT_FOUND","message":"존재하지 않거나 삭제된 사업장입니다"}`
  - DuplicateBusinessException → 409 + `{"code":"DUPLICATE_BUSINESS","message":"동일한 이름과 위치의 사업장이 이미 등록되어 있습니다"}`
  - MethodArgumentNotValidException → 400 + 필드별 에러 메시지
- **T-7.2**: EC-1 통합 테스트
  - 사업장 없는 좌표로 검색 → 200 + `{"businesses":[],"total":0}`
- **T-7.3**: EC-2 통합 테스트
  - latitude=999 → 400
  - latitude=null → 400
  - radius=3 (허용값 아님) → 400
- **T-7.4**: EC-3 통합 테스트
  - 동일 owner+name+lat+lng로 두 번 등록 → 첫 번째 201, 두 번째 409
- **T-7.5**: EC-5 E2E 시나리오 테스트
  - 사업장 등록 → 검색 결과에 포함 → 삭제 → 상세 조회 404
- **T-7.6**: Rate Limiting 테스트 (Nginx)
  - 60회 요청 → 정상 → 61번째 → 429

## 완료 기준
- [ ] GlobalExceptionHandler 모든 예외 매핑 테스트 통과
- [ ] EC-1 ~ EC-5 모든 시나리오 테스트 통과
- [ ] 에러 응답 포맷이 api.yaml ErrorResponse 스키마와 일치
- [ ] Rate Limiting 동작 확인 (EC-4)
- [ ] 한국어 에러 메시지 확인

## 관련 Spec 항목
- spec.md Edge Cases EC-1 ~ EC-5 전체
- spec.md FR-002 (반경 유효값), FR-008 (좌표 범위)
- api.yaml ErrorResponse schema
