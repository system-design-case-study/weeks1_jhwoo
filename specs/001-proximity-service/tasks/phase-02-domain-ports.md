# Phase 02: Domain 엔티티 + Port 인터페이스

**Status**: pending
**Prerequisites**: Phase 01 완료
**Covers**: plan.md (Project Structure — domain/, application/port/), spec.md (Key Entities)

## 목표
순수 도메인 엔티티와 Hexagonal Architecture의 Port 인터페이스를 정의한다. 이 계층은 인프라에 의존하지 않는다.

## Tasks

### 2.1 Domain 엔티티 생성
- `domain/Business.java`: id, name, address, latitude, longitude, phone, category, ownerId, createdAt, updatedAt
- `domain/BusinessHours.java`: id, businessId, dayOfWeek, openTime, closeTime, isClosed
- `domain/BusinessPhoto.java`: id, businessId, photoUrl, displayOrder, createdAt
- `domain/Owner.java`: id, email, passwordHash, name, createdAt, updatedAt
- 도메인 엔티티는 POJO — JPA 어노테이션 없음, 인프라 비의존

### 2.2 Inbound Port 인터페이스
- `application/port/in/SearchUseCase.java`
  - `SearchResponse search(SearchRequest request)` — FR-001, FR-002, FR-003, FR-010
- `application/port/in/BusinessUseCase.java`
  - `BusinessDetailResponse getDetail(Long id)` — FR-005
  - `BusinessDetailResponse create(BusinessCreateRequest request, Long ownerId)` — FR-006
  - `BusinessDetailResponse update(Long id, BusinessUpdateRequest request, Long ownerId)` — FR-006, FR-009
  - `void delete(Long id, Long ownerId)` — FR-006, FR-009

### 2.3 Outbound Port 인터페이스 (CQRS 분리)
- `application/port/out/SearchPort.java` — 읽기 전용, jOOQ Adapter 연결
  - `List<BusinessSummary> searchByLocation(double lat, double lng, double radiusMeters, int page, int size)`
  - `long countByLocation(double lat, double lng, double radiusMeters)`
- `application/port/out/BusinessReadPort.java` — 읽기 전용, jOOQ Adapter 연결
  - `Optional<BusinessDetail> findById(Long id)`
- `application/port/out/BusinessWritePort.java` — 쓰기 전용, JPA Adapter 연결
  - `Business save(Business business)`
  - `void deleteById(Long id)`
  - `Optional<Business> findByIdForWrite(Long id)` — 쓰기 경로에서 소유권 확인용
  - `boolean existsByOwnerAndNameAndLocation(Long ownerId, String name, double lat, double lng)` — EC-3 대응
- `application/port/out/CachePort.java`
  - `Optional<SearchResponse> getSearchCache(String cacheKey)`
  - `void putSearchCache(String cacheKey, SearchResponse response)`
  - `Optional<BusinessDetailResponse> getBusinessCache(Long id)`
  - `void putBusinessCache(Long id, BusinessDetailResponse response)`
  - `void invalidateSearchCache(double lat, double lng)` — 3×3 Grid 무효화
  - `void invalidateBusinessCache(Long id)`
- `application/port/out/AuthPort.java`
  - `Long extractOwnerId(String token)`
  - `boolean validateToken(String token)`

### 2.4 Application DTO
- `application/dto/SearchRequest.java`: latitude, longitude, radius, page, size
- `application/dto/SearchResponse.java`: businesses (List), total, page, size
- `application/dto/BusinessSummary.java`: id, name, address, distance, category
- `application/dto/BusinessDetailResponse.java`: 전체 필드 + hours + photos
- `application/dto/BusinessCreateRequest.java`: name, address, latitude, longitude, phone, category, businessHours
- `application/dto/BusinessUpdateRequest.java`: 동일 필드 (nullable)

## TDD 접근

### 테스트 우선 작성
- **T-2.1**: Domain 엔티티 생성/검증 단위 테스트
  - Business 생성 시 위도/경도 범위 검증 (EC-2 도메인 레벨)
  - BusinessHours dayOfWeek 범위 검증 (0~6)
- **T-2.2**: DTO Validation 테스트
  - SearchRequest: latitude null → 실패, radius 유효값 아닌 경우 → 실패
  - BusinessCreateRequest: name blank → 실패, latitude 범위 초과 → 실패

## 완료 기준
- [ ] Domain 엔티티 4개 생성, 단위 테스트 통과
- [ ] Inbound Port 2개, Outbound Port 4개 인터페이스 정의
- [ ] DTO 6개 생성, Validation 테스트 통과
- [ ] 도메인 계층이 Spring/JPA/jOOQ에 의존하지 않음 확인

## 관련 Spec 항목
- spec.md Key Entities (Business, Owner, Customer)
- spec.md FR-001 ~ FR-010
- spec.md EC-2 (좌표 검증), EC-3 (중복 등록)
- plan.md Project Structure (application/, domain/)
- research.md Decision 7 (CQRS Port 분리)
