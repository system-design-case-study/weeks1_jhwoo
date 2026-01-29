# Phase 03: 쓰기 경로 — JPA Entity + Write Adapter + CRUD UseCase

**Status**: pending
**Prerequisites**: Phase 02 완료
**Covers**: plan.md (write/ Adapter), research.md (Decision 7 — JPA Write), spec.md (User Story 3, EC-3)

## 목표
JPA Entity 매핑, Write Adapter(JPA 기반), BusinessUseCase의 CUD 로직을 구현한다.

## Tasks

### 3.1 JPA Entity 매핑
- `adapter/out/persistence/write/entity/BusinessJpaEntity.java`
  - `@Entity`, Hibernate Spatial `@Column(columnDefinition = "geography(Point,4326)")` 매핑
  - Domain ↔ JPA Entity 변환 메서드 (`toDomain()`, `fromDomain()`)
- `adapter/out/persistence/write/entity/BusinessHoursJpaEntity.java`
- `adapter/out/persistence/write/entity/BusinessPhotoJpaEntity.java`
- `adapter/out/persistence/write/entity/OwnerJpaEntity.java`

### 3.2 JPA Repository
- `adapter/out/persistence/write/BusinessJpaRepository.java` (Spring Data JPA)
  - `existsByOwnerIdAndNameAndLatitudeAndLongitude()` — EC-3 중복 검증

### 3.3 Write Adapter 구현
- `adapter/out/persistence/write/JpaBusinessWriteAdapter.java` implements `BusinessWritePort`
  - `save()`: Domain → JPA Entity 변환 → 저장 → Domain 반환
  - `deleteById()`: 삭제
  - `findByIdForWrite()`: 소유권 확인용 조회 (Primary DB)
  - `existsByOwnerAndNameAndLocation()`: EC-3 중복 확인

### 3.4 BusinessService — CUD 로직
- `application/service/BusinessService.java` implements `BusinessUseCase`
  - `create()`:
    1. EC-3: `BusinessWritePort.existsByOwnerAndNameAndLocation()` → true면 409 Conflict
    2. Domain Business 생성 → `BusinessWritePort.save()`
    3. 캐시 무효화: `CachePort.invalidateSearchCache(lat, lng)`
  - `update()`:
    1. `BusinessWritePort.findByIdForWrite()` → 없으면 404 (EC-5)
    2. FR-009: `ownerId` 일치 확인 → 불일치 시 403
    3. 도메인 엔티티 수정 → `BusinessWritePort.save()`
    4. 캐시 무효화
  - `delete()`:
    1. `BusinessWritePort.findByIdForWrite()` → 없으면 404
    2. FR-009: `ownerId` 일치 확인 → 불일치 시 403
    3. `BusinessWritePort.deleteById()`
    4. 캐시 무효화

### 3.5 BusinessController — CUD 엔드포인트
- `adapter/in/web/BusinessController.java`
  - `POST /api/businesses` → 201 Created (FR-006)
  - `PUT /api/businesses/{id}/update` → 200 OK (FR-006)
  - `DELETE /api/businesses/{id}/delete` → 204 No Content (FR-006)
  - 인증 필요: `@PreAuthorize` 또는 수동 토큰 검증
  - EC-2: `@Valid` + Bean Validation으로 좌표 범위 검증

## TDD 접근

### 테스트 우선 작성
- **T-3.1**: JPA Entity ↔ Domain 변환 테스트
  - BusinessJpaEntity.fromDomain() → toDomain() 왕복 변환 정합성
- **T-3.2**: Write Adapter 통합 테스트 (Testcontainers)
  - save → findByIdForWrite 확인
  - existsByOwnerAndNameAndLocation 중복 검사
  - deleteById 후 findByIdForWrite → empty
- **T-3.3**: BusinessService 단위 테스트 (Port Mock)
  - create: 정상 생성 → save 호출, 캐시 무효화 호출 확인
  - create: 중복 → 409 Conflict 예외
  - update: 소유주 불일치 → 403 예외 (FR-009)
  - update: 존재하지 않는 ID → 404 예외 (EC-5)
  - delete: 정상 삭제 → deleteById + 캐시 무효화 호출 확인
  - delete: 소유주 불일치 → 403 예외
- **T-3.4**: BusinessController 통합 테스트 (@WebMvcTest)
  - POST /api/businesses: 유효한 요청 → 201
  - POST /api/businesses: 좌표 범위 초과 → 400 (EC-2)
  - POST /api/businesses: 중복 → 409 (EC-3)
  - PUT: 소유주 불일치 → 403 (FR-009)
  - DELETE: 정상 → 204

## 완료 기준
- [ ] JPA Entity 4개, Domain 변환 테스트 통과
- [ ] Write Adapter 통합 테스트 통과 (Testcontainers)
- [ ] BusinessService CUD 단위 테스트 통과
- [ ] BusinessController CUD 통합 테스트 통과
- [ ] EC-2, EC-3, FR-009 시나리오 테스트 통과

## 관련 Spec 항목
- spec.md User Story 3 (사업장 정보 관리) 전체
- spec.md FR-006, FR-007, FR-008, FR-009
- spec.md EC-2 (유효하지 않은 좌표), EC-3 (중복 등록), EC-5 (삭제 후 조회)
- research.md Decision 7 (JPA Write 경로)
- api.yaml: POST/PUT/DELETE 엔드포인트
