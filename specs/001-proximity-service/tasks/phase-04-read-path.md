# Phase 04: 읽기 경로 — jOOQ Read Adapter + 검색/상세조회 UseCase

**Status**: pending
**Prerequisites**: Phase 03 완료
**Covers**: plan.md (read/ Adapter), research.md (Decision 1, 7), spec.md (User Story 1, 2), data-model.md (Read Path Optimization)

## 목표
jOOQ 기반 Read Adapter로 PostGIS ST_DWithin 검색과 상세 조회를 구현한다.

## Tasks

### 4.1 PostGIS 커스텀 함수 바인딩
- jOOQ에서 사용할 PostGIS 함수 정의:
  - `stDWithin(Field, Field, double)` → `ST_DWithin(location, ST_MakePoint(?, ?)::geography, ?)`
  - `stDistance(Field, Field)` → `ST_Distance(location, ST_MakePoint(?, ?)::geography)`
  - `stMakePoint(double lng, double lat)` → `ST_MakePoint(?, ?)::geography`

### 4.2 JooqSearchAdapter 구현
- `adapter/out/persistence/read/JooqSearchAdapter.java` implements `SearchPort`
  - `searchByLocation()`: jOOQ DSL로 ST_DWithin 쿼리 실행
    - data-model.md의 jOOQ 검색 쿼리 예시 구현
    - 거리 순 정렬 (FR-010)
    - 페이지네이션 (LIMIT/OFFSET)
  - `countByLocation()`: 결과 건수 조회

### 4.3 JooqBusinessReadAdapter 구현
- `adapter/out/persistence/read/JooqBusinessReadAdapter.java` implements `BusinessReadPort`
  - `findById()`: 사업장 상세 정보 + business_hours + business_photos JOIN 조회
  - EC-5: 삭제된 사업장 조회 시 `Optional.empty()` 반환

### 4.4 SearchService 구현
- `application/service/SearchService.java` implements `SearchUseCase`
  - `search()`:
    1. `SearchPort.searchByLocation()` 호출 (Replica DB via jOOQ)
    2. 결과를 `SearchResponse`로 변환
    3. EC-1: 결과가 없으면 빈 리스트 + total=0 반환 (200 OK)
  - FR-002: radius 유효값 검증 (0.5, 1, 2, 5, 20km)

### 4.5 BusinessService — 상세 조회 (Read)
- `BusinessService.getDetail()`:
  1. `BusinessReadPort.findById()` 호출
  2. 결과 없으면 404 Not Found (EC-5: "존재하지 않거나 삭제된 사업장입니다")
  3. `BusinessDetailResponse`로 변환

### 4.6 SearchController + BusinessController (Read)
- `adapter/in/web/SearchController.java`
  - `GET /api/search` → 200 OK (FR-001, FR-003)
  - EC-2: `@Valid` 좌표 범위 검증 → 400
- `adapter/in/web/BusinessController.java`
  - `GET /api/businesses/{id}` → 200 OK (FR-005)
  - EC-5: 존재하지 않는 ID → 404

## TDD 접근

### 테스트 우선 작성
- **T-4.1**: PostGIS 함수 바인딩 통합 테스트 (Testcontainers)
  - ST_DWithin 쿼리가 올바른 SQL 생성하는지 확인
  - 테스트 데이터 삽입 후 반경 내/외 사업장 필터링 확인
- **T-4.2**: JooqSearchAdapter 통합 테스트 (Testcontainers)
  - 10개 사업장 삽입 → 1km 반경 검색 → 반경 내 사업장만 반환
  - 거리 순 정렬 확인 (FR-010)
  - 반경 내 사업장 없음 → 빈 리스트 반환 (EC-1)
  - 페이지네이션: page=0, size=5 → 5개 반환
- **T-4.3**: JooqBusinessReadAdapter 통합 테스트 (Testcontainers)
  - 사업장 + 영업시간 + 사진 삽입 → findById → 전체 필드 확인
  - 존재하지 않는 ID → Optional.empty()
- **T-4.4**: SearchService 단위 테스트 (Port Mock)
  - 정상 검색 → SearchResponse 반환
  - 빈 결과 → total=0, businesses=[] (EC-1)
  - 유효하지 않은 radius → 400 예외
- **T-4.5**: SearchController 통합 테스트 (@WebMvcTest)
  - GET /api/search?latitude=37.5665&longitude=126.9780&radius=1 → 200
  - GET /api/search?latitude=999 → 400 (EC-2)
  - GET /api/search (latitude 누락) → 400 (EC-2)
- **T-4.6**: BusinessController GET 통합 테스트 (@WebMvcTest)
  - GET /api/businesses/1 → 200
  - GET /api/businesses/99999 → 404 (EC-5)

## 완료 기준
- [ ] jOOQ PostGIS 함수 바인딩 동작 확인
- [ ] JooqSearchAdapter 통합 테스트 통과 (ST_DWithin, 정렬, 페이지네이션)
- [ ] JooqBusinessReadAdapter 통합 테스트 통과
- [ ] SearchService 단위 테스트 통과
- [ ] Controller 통합 테스트 통과
- [ ] EC-1, EC-2, EC-5 시나리오 테스트 통과
- [ ] FR-001, FR-002, FR-003, FR-005, FR-010 커버

## 관련 Spec 항목
- spec.md User Story 1 (주변 사업장 검색) 전체
- spec.md User Story 2 (사업장 상세 정보 조회) 전체
- spec.md FR-001, FR-002, FR-003, FR-004, FR-005, FR-010
- spec.md EC-1 (빈 결과), EC-2 (좌표 검증), EC-5 (삭제 후 조회)
- research.md Decision 1 (PostGIS ST_DWithin)
- research.md Decision 7 (CQRS — jOOQ Read)
- data-model.md (Read Path Optimization, jOOQ 쿼리 예시)
- api.yaml: GET /search, GET /businesses/{id}
