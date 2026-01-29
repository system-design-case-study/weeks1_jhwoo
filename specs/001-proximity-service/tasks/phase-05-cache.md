# Phase 05: 캐시 계층 — Redis + Grid-based Cache Key

**Status**: pending
**Prerequisites**: Phase 04 완료
**Covers**: research.md (Decision 3), plan.md (CachePort), spec.md (SC-001, SC-004)

## 목표
Redis 캐시 계층을 구현하여 검색 결과와 사업장 상세 정보를 Grid-based 키로 캐싱한다. 캐시 무효화 전략(TTL + Event-driven)을 적용한다.

## Tasks

### 5.1 RedisConfig 설정
- `config/RedisConfig.java`
  - RedisTemplate Bean 설정 (Jackson 직렬화)
  - Redis connection 설정 (application.yml)

### 5.2 Grid 반올림 유틸리티
- 캐시 키 생성 로직:
  - `Math.round(lat / 0.01) * 0.01` → 0.01도 단위 Grid 반올림
  - 캐시 키 포맷: `search:{roundedLat}:{roundedLng}:{radius}`
  - 사업장 상세: `business:{id}`

### 5.3 RedisCacheAdapter 구현
- `adapter/out/cache/RedisCacheAdapter.java` implements `CachePort`
  - `getSearchCache()`: Grid 반올림 키로 Redis 조회
  - `putSearchCache()`: TTL 10분으로 저장
  - `getBusinessCache()`: 사업장 ID 키로 Redis 조회
  - `putBusinessCache()`: TTL 30분으로 저장
  - `invalidateSearchCache(lat, lng)`: 해당 Grid + 인접 8개 Grid 캐시 삭제 (3×3)
  - `invalidateBusinessCache(id)`: 사업장 상세 캐시 삭제

### 5.4 SearchService에 캐시 통합
- `SearchService.search()` 수정:
  1. Grid 반올림 → 캐시 키 생성
  2. `CachePort.getSearchCache()` → Cache Hit 시 즉시 반환
  3. Cache Miss → `SearchPort.searchByLocation()` 호출 → 결과 캐싱
- Cache-aside 패턴 적용

### 5.5 BusinessService에 캐시 통합
- `BusinessService.getDetail()` 수정:
  1. `CachePort.getBusinessCache()` → Cache Hit 시 즉시 반환
  2. Cache Miss → `BusinessReadPort.findById()` 호출 → 결과 캐싱
- CUD 시 캐시 무효화 (Phase 03에서 이미 호출 구조 준비)

## TDD 접근

### 테스트 우선 작성
- **T-5.1**: Grid 반올림 단위 테스트
  - (37.5665, 126.9780) → 반올림 (37.57, 126.98) 확인
  - 동일 Grid 내 다른 좌표 → 동일 캐시 키 생성 확인
  - 다른 Grid → 다른 캐시 키 생성 확인
- **T-5.2**: RedisCacheAdapter 통합 테스트 (Testcontainers — Redis)
  - putSearchCache → getSearchCache → 동일 데이터 반환
  - TTL 만료 후 → getSearchCache → empty
  - invalidateSearchCache: 3×3 Grid 캐시 삭제 확인
- **T-5.3**: SearchService 캐시 통합 단위 테스트 (Port Mock)
  - Cache Hit → SearchPort 호출 안 됨 확인
  - Cache Miss → SearchPort 호출 + 캐싱 확인
- **T-5.4**: BusinessService 캐시 통합 단위 테스트 (Port Mock)
  - Cache Hit → BusinessReadPort 호출 안 됨 확인
  - CUD 후 캐시 무효화 호출 확인
- **T-5.5**: Privacy 검증 테스트
  - 캐시 키에 원본 좌표가 아닌 반올림 좌표만 포함되는지 확인
  - 반올림 키에서 원본 좌표 복원 불가능 검증

## 완료 기준
- [ ] RedisConfig 설정, Redis 연결 확인
- [ ] Grid 반올림 유틸리티 테스트 통과
- [ ] RedisCacheAdapter 통합 테스트 통과 (put/get/invalidate/TTL)
- [ ] SearchService 캐시 통합 테스트 통과 (Hit/Miss)
- [ ] BusinessService 캐시 통합 테스트 통과
- [ ] Privacy: 캐시 키에 반올림 좌표만 사용 확인

## 관련 Spec 항목
- research.md Decision 3 (Redis + Grid-based Cache Key) 전체
  - Cache Key 설계, Grid 크기별 히트율, Privacy 영향, Invalidation, Memory 추정
- plan.md Architecture Diagram (CachePort 연결)
- spec.md SC-001 (검색 p95 <1초), SC-004 (상세 조회 p95 <0.5초)
- spec.md Constitution II (Privacy by Design)
