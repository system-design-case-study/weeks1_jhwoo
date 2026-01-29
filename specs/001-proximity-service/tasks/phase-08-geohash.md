# Phase 08: Manual Geohash 구현 (학습용)

**Status**: pending
**Prerequisites**: Phase 04 완료
**Covers**: research.md (Decision 1 — Manual Geohash), plan.md (domain/Geohash.java), api.yaml (/search/geohash)

## 목표
라이브러리 없이 Geohash 알고리즘을 직접 구현하고, PostGIS 검색 결과와 비교할 수 있는 학습용 API를 제공한다.

## Tasks

### 8.1 Geohash.java 구현
- `domain/Geohash.java` (순수 도메인, 인프라 비의존)
- **인코딩**: (latitude, longitude, precision) → Base32 문자열
  1. 위도(-90~90), 경도(-180~180)를 이진 탐색으로 비트열 생성
  2. 경도 비트와 위도 비트를 번갈아 인터리빙 (Z-order curve)
  3. 5비트씩 묶어 Base32 인코딩 (`0123456789bcdefghjkmnpqrstuvwxyz`)
- **디코딩**: Base32 문자열 → (minLat, maxLat, minLng, maxLng) 경계 범위
  1. Base32 → 5비트씩 복원
  2. 짝수 비트 → 경도, 홀수 비트 → 위도 분리
  3. 비트열 → 이진 탐색 역추적 → 경계 범위 산출
- **Neighbor 계산**: 인접 8방향 Geohash 셀 산출
  - 셀 경계 문제(boundary artifact) 확인을 위해 필요

### 8.2 Geohash 기반 검색 로직
- `application/port/in/GeohashSearchUseCase.java`
  - `GeohashSearchResponse searchByGeohash(double lat, double lng, int precision, int page, int size)`
- `application/service/GeohashSearchService.java`
  1. (lat, lng) → Geohash 인코딩 (precision)
  2. 해당 Geohash + 8방향 neighbor Geohash 목록 생성
  3. 각 Geohash 셀의 경계 범위(bounding box) 계산
  4. bounding box 합집합 내 사업장을 DB에서 조회
  5. 셀 경계(bounds) 정보와 함께 결과 반환

### 8.3 GeohashSearchController
- `adapter/in/web/SearchController.java`에 엔드포인트 추가
  - `GET /api/search/geohash?latitude=&longitude=&precision=&page=&size=`
  - 응답: `GeohashSearchResponse` (geohash, precision, cellBounds, businesses, total, page, size)
  - api.yaml의 GeohashSearchResponse 스키마 준수

### 8.4 Geohash DTO
- `application/dto/GeohashSearchResponse.java`
  - geohash (String)
  - precision (int)
  - cellBounds (minLatitude, maxLatitude, minLongitude, maxLongitude)
  - businesses (List<BusinessSummary>)
  - total, page, size

## TDD 접근

### 테스트 우선 작성
- **T-8.1**: Geohash 인코딩 단위 테스트
  - (37.5665, 126.9780, 6) → 알려진 Geohash 문자열 확인
  - precision 4 → 4자, precision 7 → 7자
  - 동일 좌표, 같은 precision → 동일 Geohash (결정론적)
- **T-8.2**: Geohash 디코딩 단위 테스트
  - 인코딩 → 디코딩 → 원본 좌표가 경계 범위 내에 포함 확인
  - 경계 범위 크기가 precision별 셀 크기 분석 테이블과 일치 확인
    - precision 4: ~20km × 14km
    - precision 6: ~1.2km × 0.44km
    - precision 7: ~156m × 110m
- **T-8.3**: Neighbor 계산 테스트
  - 인접 8방향 Geohash가 실제로 공간적으로 인접하는지 확인
  - Neighbor의 디코딩 경계가 원본 셀과 겹치지 않되, 간격 없이 인접 확인
- **T-8.4**: 셀 경계 문제(boundary artifact) 검증 테스트
  - 셀 경계 근처 좌표 → Geohash 인코딩 → 인접 셀 사업장 누락 확인 (학습 목적)
  - PostGIS ST_DWithin 결과와 비교 → Geohash에서 누락되는 사업장 식별
- **T-8.5**: GeohashSearchService 통합 테스트
  - 사업장 삽입 → Geohash 검색 → 셀 + neighbor 내 사업장 반환 확인
- **T-8.6**: GeohashSearchController 통합 테스트
  - GET /api/search/geohash?latitude=37.5665&longitude=126.9780&precision=6 → 200
  - 응답에 geohash, cellBounds, businesses 포함 확인

## 완료 기준
- [ ] Geohash 인코딩/디코딩 단위 테스트 통과
- [ ] Neighbor 계산 테스트 통과
- [ ] 셀 경계 문제 검증 테스트 통과 (PostGIS 비교)
- [ ] GeohashSearchService 통합 테스트 통과
- [ ] `/api/search/geohash` 엔드포인트 동작 확인
- [ ] 라이브러리 미사용 확인 (순수 Java 구현)

## 관련 Spec 항목
- research.md Decision 1 — Manual Geohash 보조 구현 (학습용) 전체
  - 알고리즘 개요, 학습 목적, 정밀도별 셀 크기 분석
- plan.md (domain/Geohash.java)
- plan.md Complexity Tracking (Manual Geohash 학습 목적)
- api.yaml: GET /search/geohash, GeohashSearchResponse schema
