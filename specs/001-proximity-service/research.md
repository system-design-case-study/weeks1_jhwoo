# Research: 주변 사업장 검색 서비스 (Proximity Service)

## Decision 1: Geospatial Algorithm — PostGIS ST_DWithin + Grid-based Caching

### Decision
PostGIS의 `ST_DWithin`을 primary 공간 검색 알고리즘으로 채택하고, 캐시 키는 좌표 Grid 반올림 방식을 사용한다.

### Rationale
1. **정확한 반경 검색**: `ST_DWithin`은 정확한 거리 기반 검색을 제공하며, Geohash의 셀 경계 문제(boundary artifact)가 없다. 후처리(Haversine) 필터링이 불필요하다.
2. **GIST 인덱스 성능**: PostGIS의 GIST 인덱스는 공간 쿼리에 최적화되어 있으며, 100만 건 기준 15~60ms의 쿼리 성능을 제공한다.
3. **캐싱 효율**: 좌표 Grid 반올림(`Math.round(lat / 0.01) * 0.01`)으로 deterministic 캐시 키를 생성하여, 인근 사용자들이 동일 캐시를 공유한다.
4. **구현 단순성**: 별도 라이브러리 없이 PostgreSQL 내장 함수로 거리 계산, 반경 검색, 정렬을 모두 처리한다.

### Grid-based Cache Key 전략

```
캐시 키: search:{roundedLat}:{roundedLng}:{radius}
예시:   search:37.57:126.98:1km

Grid 반올림: 0.01도 단위 (약 1.1km × 0.88km at 37°N)
→ 인접 사용자가 동일 Grid에 매핑되어 캐시 적중률 60~80% 달성 가능
```

### PostGIS 검색 쿼리 예시

```sql
SELECT id, name, address,
       ST_Distance(location, ST_MakePoint(:lng, :lat)::geography) AS distance
FROM businesses
WHERE ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
ORDER BY distance
LIMIT 20;
```

### Alternatives Considered

| 대안 | 장점 | 선택하지 않은 이유 |
| ---- | ---- | ------------------ |
| **Geohash (B-tree)** | 캐싱 키가 자연스러움, MySQL 호환 | 셀 경계 문제로 8방향 neighbor 조회 필수, 후처리(Haversine) 필터링 필요, 정확도 저하 |
| **PostGIS + Geohash 혼용** | Geohash 캐시 + PostGIS 정확도 | 이중 인덱싱 복잡도 증가, Grid 반올림으로 캐시 효율 달성 가능하므로 Geohash 불필요 |
| **Query Hash (좌표+반경 MD5)** | 구현 단순 | 미세 좌표 차이도 캐시 미스 유발, 적중률 극히 낮음 |
| **Quadtree** | 데이터 밀도 adaptive 분할 | RDB에 직렬화 비효율적, 메모리 기반 구조라 Stateless 원칙 위배 |

### Manual Geohash 보조 구현 (학습용)

운영 검색은 PostGIS `ST_DWithin`이 담당하지만, 공간 인덱싱 원리를 이해하기 위해 **라이브러리 없이 Geohash를 직접 구현**한다.

**구현 위치**: `domain/Geohash.java`

**알고리즘 개요**:
1. 위도(-90~90)와 경도(-180~180)를 각각 이진 탐색으로 비트열 생성
2. 경도 비트와 위도 비트를 번갈아 인터리빙 (Z-order curve)
3. 5비트씩 묶어 Base32 인코딩 (`0123456789bcdefghjkmnpqrstuvwxyz`)
4. 디코딩: Base32 → 비트열 → 경도/위도 범위 역추출

**학습 목적**:
- 2D 좌표 → 1D 문자열 매핑의 원리 (Z-order curve, bit interleaving)
- Prefix 공유 = 공간적 근접성이라는 Geohash의 핵심 속성 체득
- 셀 경계 문제(boundary artifact)를 직접 실험하여 PostGIS 선택 근거 검증

**Geohash 정밀도별 셀 크기 분석**:

| Precision | 비트 수 | 셀 크기 (위도 × 경도) | 대략적 면적 | 용도 |
|-----------|---------|----------------------|------------|------|
| 4 | 20 | ~0.18° × 0.18° (~20km × 14km) | ~280km² | 광역 검색 |
| 5 | 25 | ~0.044° × 0.044° (~4.9km × 3.5km) | ~17km² | 도시 단위 |
| 6 | 30 | ~0.011° × 0.0055° (~1.2km × 0.44km) | ~0.53km² | 동네 단위 |
| 7 | 35 | ~0.0014° × 0.0014° (~156m × 110m) | ~0.017km² | 블록 단위 |

**비교 API**: `/api/search/geohash` 엔드포인트를 통해 PostGIS 결과와 Geohash 결과를 비교할 수 있다. 이를 통해 셀 경계 문제와 정밀도-성능 trade-off를 실제로 확인한다.

### Trade-off 요약
PostGIS `ST_DWithin`은 GIST 인덱스 유지 비용이 B-tree 대비 높지만, Read:Write 2500:1 환경에서 쓰기 빈도가 극히 낮아(~0.12 QPS) 인덱스 갱신 부담이 무시할 수 있다. Grid 반올림 캐시 키는 Geohash 대비 정밀도 조절이 자유롭고, 반경별 독립 캐시가 가능하다.

---

## Decision 2: Database — PostgreSQL 16 + PostGIS 3.4 + Streaming Replication

### Decision
PostgreSQL 16 + PostGIS 3.4를 사용하며, Streaming Replication(비동기)으로 Primary 1대 + Replica 2대 구조를 채택한다.

### Rationale
1. **PostGIS 필수**: `ST_DWithin`, GIST 인덱스, `GEOGRAPHY` 타입 등 공간 검색 핵심 기능이 PostgreSQL + PostGIS에서만 네이티브 지원된다.
2. **QPS 대응**: 피크 읽기 QPS ~1,450은 PostgreSQL 단일 인스턴스로도 처리 가능하나, 가용성과 부하 분산을 위해 Replica 2대 운영.
3. **Replication Lag 허용**: 24시간 Eventual Consistency 윈도우가 있으므로, 비동기 Streaming Replication의 수 초~수 분 lag는 무시할 수 있다.
4. **Read/Write 분리**: Spring Boot의 `AbstractRoutingDataSource`로 `@Transactional(readOnly = true)` 기반 자동 라우팅 구현.
5. **Connection Pooling**: HikariCP 기본 설정, Primary pool과 Replica pool 분리 운영.

### Topology

```
              ┌──────────┐
   Writes ───▶│ Primary  │
              └────┬─────┘
                   │ Streaming Replication (async)
          ┌────────┴────────┐
          ▼                 ▼
   ┌──────────┐      ┌──────────┐
   │ Replica 1│      │ Replica 2│
   └──────────┘      └──────────┘
          ▲                 ▲
          └────────┬────────┘
                   │
              Load Balancer
                   │
              Reads (LBS)
```

### Alternatives Considered

| 대안 | 장점 | 선택하지 않은 이유 |
| ---- | ---- | ------------------ |
| **MySQL 8.0** | 널리 사용, 운영 친숙 | PostGIS의 GIST 인덱스, `GEOGRAPHY` 타입 미지원. `ST_Distance_Sphere` 성능 이슈 |
| **단일 PostgreSQL** | 구성 단순 | 단일 장애점(SPOF), 피크 시 성능 불안 |
| **Replica 3대 이상** | 더 높은 가용성 | 현재 QPS에서 과잉, 운영 비용 증가 |

---

## Decision 3: Caching — Redis + Grid-based Cache Key

### Decision
Redis를 캐시 계층으로 도입하여 Grid-based 키로 검색 결과와 사업장 상세 정보를 캐싱한다.

### 캐시 필요성 분석

피크 읽기 QPS ~1,450은 PostgreSQL Replica로 처리 가능하지만, 캐시 도입 이유:
1. **Latency 개선**: DB 쿼리 20~80ms → Redis 조회 1~5ms (Constitution: Performance First)
2. **DB 부하 경감**: 캐시 적중 시 DB 접근 완전 회피, Replica 여유 확보
3. **피크 대응**: 피크 배율 5배 이상 상황에서도 안정적 성능 보장
4. **비용 효율**: Read:Write 2500:1에서 캐시 효과 극대화

### Cache Key 설계

**검색 결과 캐시:**
```
key:   search:{roundedLat}:{roundedLng}:{radius}
value: List<BusinessSummary> (id, name, address, distance)
TTL:   10분
예시:  search:37.57:126.98:1km → [{"id":1,"name":"...","distance":0.3}, ...]
```

**사업장 상세 캐시:**
```
key:   business:{id}
value: BusinessDetail (모든 필드)
TTL:   30분
예시:  business:12345 → {"id":12345,"name":"...","hours":"...","photos":[...]}
```

### Grid 크기별 캐시 히트율 시뮬레이션

| Grid 크기 | 대략적 셀 크기 (at 37°N) | 예상 캐시 히트율 | 정밀도 | 비고 |
|-----------|------------------------|----------------|--------|------|
| 0.001도 | ~111m × 88m | 5~15% | 매우 높음 | 미세 좌표 차이로 캐시 미스 빈번, 실질적 캐싱 효과 미미 |
| 0.005도 | ~555m × 440m | 30~50% | 높음 | 도보 이동 범위 내 캐시 공유 가능, 중간 수준 |
| **0.01도** | **~1.1km × 0.88km** | **60~80%** | **적정** | **채택 — 히트율과 정밀도 균형점** |
| 0.05도 | ~5.5km × 4.4km | 85~95% | 낮음 | 히트율은 높으나 광범위한 사용자가 동일 결과 수신, 검색 정밀도 저하 |

**채택 근거**: 0.01도 Grid는 약 1.1km² 셀 내 사용자들이 동일 캐시를 공유하여, 도심 환경에서 60~80%의 캐시 적중률을 달성한다. 이보다 작으면 히트율이 급감하고, 크면 검색 결과의 사용자별 관련성이 떨어진다.

### Privacy 영향 분석

Grid 반올림은 캐시 효율뿐 아니라 **사용자 위치 프라이버시**에도 직접적 영향을 미친다.

| Grid 크기 | Privacy 수준 | 설명 |
|-----------|-------------|------|
| 0.001도 | 낮음 | ~111m 단위 → 특정 건물 수준까지 위치 추론 가능 |
| 0.01도 | **적정** | ~1.1km 단위 → 동 단위 수준, 정밀 추적 불가 |
| 0.05도 | 높음 | ~5.5km 단위 → 구 단위 수준, 위치 추론 매우 어려움 |

- **0.01도 Grid 채택 시 Privacy**: 약 1.1km² 셀 내 모든 사용자가 동일 캐시 키를 공유하므로, 캐시 키만으로 개별 사용자의 정확한 위치를 추론할 수 없다
- **원본 좌표 복원 불가능**: `Math.round(lat / 0.01) * 0.01` 연산은 비가역적 — 반올림된 좌표에서 원본 좌표(소수점 6자리)를 역추적할 수 없음
- **Constitution II (Privacy by Design) 충족**: 서버 측에서 사용자 원본 좌표를 캐시 키에 저장하지 않으며, Grid 반올림된 좌표만 Redis에 기록

### Cache Invalidation 전략

- **Time-based (TTL)**: 검색 결과 10분, 상세 정보 30분 TTL
- **Event-driven**: 사업장 정보 변경 시 해당 Grid 좌표 + 3×3 주변 Grid 캐시 삭제
  - 변경된 사업장 좌표를 Grid 반올림 후, 인접 8개 Grid의 캐시도 함께 삭제
- **Lazy Invalidation**: TTL 만료 시 DB에서 재조회 (24시간 Consistency 윈도우 내)
- Write QPS가 ~0.12로 극히 낮아 invalidation 부하 무시 가능

### Memory 추정

```
검색 결과 캐시:
- Grid 셀 수: ~30,000 (한국 전체, 0.01도 Grid 기준)
- 반경 옵션: 5개
- 캐시 엔트리: 30,000 × 5 = 150,000
- 엔트리당 크기: ~2KB (20개 사업장 요약)
- 총: ~300MB

사업장 상세 캐시:
- 활성 캐시 (핫 데이터): ~100,000건 (전체의 10%)
- 엔트리당 크기: ~1KB
- 총: ~100MB

Redis 총 메모리: ~400MB (충분히 단일 인스턴스로 운영 가능)
```

### Alternatives Considered

| 대안 | 장점 | 선택하지 않은 이유 |
| ---- | ---- | ------------------ |
| **캐시 미사용** | 구성 단순 | Latency 목표 (p95 <1초) 달성에 여유 부족, 피크 대응 불안 |
| **Local Cache (Caffeine)** | 네트워크 비용 없음 | Stateless 원칙 위배, 인스턴스 간 불일치 |
| **Redis Geospatial (GEOADD/GEORADIUS)** | Redis 내장 공간 검색 | 전체 1M 데이터를 Redis에 적재 필요 (~100MB), DB와 이중 관리 복잡 |

---

## Decision 4: 전체 아키텍처 — Hexagonal Architecture

### Decision
Hexagonal Architecture (Port/Adapter 패턴)를 채택하여 도메인 로직과 인프라를 분리한다.

### Rationale
1. **테스트 용이성**: Domain Core가 인프라에 의존하지 않으므로 단위 테스트가 간단하다.
2. **인프라 교체 유연성**: PostGIS → 다른 공간 DB로 전환 시 Adapter만 교체하면 된다.
3. **관심사 분리**: 검색 로직(SearchUseCase)과 사업장 관리 로직(BusinessUseCase)이 명확히 분리된다.
4. **시스템 설계 학습**: Port/Adapter 패턴을 실제 적용하여 아키텍처 이해도를 높인다.

### 구조

```
Inbound Adapter          Domain Core              Outbound Adapter
─────────────────   ─────────────────────   ─────────────────────
REST Controller  →  UseCase (Port/In)    →  SearchPort → PostGISAdapter
                    - SearchUseCase          CachePort  → RedisAdapter
                    - BusinessUseCase        AuthPort   → JwtAdapter
                    Domain Entities
```

### Request Flow

```
[Client (Browser)]
       │
       ▼
[Load Balancer (Nginx)]
       │
       ├── /api/search   ──▶ [LBS (Spring Boot)]
       │                          │
       │                          ├─ Cache Hit? ──▶ [Redis] ──▶ 응답
       │                          │
       │                          └─ Cache Miss ──▶ [DB Replica] ──▶ Redis 저장 ──▶ 응답
       │
       ├── /api/business/{id} ──▶ [Business Service (Spring Boot)]
       │                               │
       │                               ├─ Cache Hit? ──▶ [Redis] ──▶ 응답
       │                               │
       │                               └─ Cache Miss ──▶ [DB Replica] ──▶ Redis 저장 ──▶ 응답
       │
       └── /api/business (POST/PUT/DELETE) ──▶ [Business Service]
                                                    │
                                                    ├─ [DB Primary] ──▶ Write
                                                    │
                                                    └─ [Redis] ──▶ Invalidate 관련 캐시
```

### 컨테이너 구성 (Docker Compose)

```
services:
  nginx              - Load Balancer / Reverse Proxy
  lbs-service        - LBS (검색 전용, Stateless)
  business-service   - 사업장 CRUD + 상세 조회
  postgres-primary   - Primary DB (PostgreSQL 16 + PostGIS 3.4)
  postgres-replica-1 - Read Replica
  postgres-replica-2 - Read Replica
  redis              - Cache Layer
  prometheus         - Metrics 수집
  grafana            - 모니터링 Dashboard
  frontend           - SPA (1페이지 웹 앱)
```

---

## Decision 5: 부하 테스트 — K6 + Prometheus + Grafana

### Decision
K6를 부하 테스트 도구로 채택하고, Prometheus + Grafana로 메트릭을 모니터링한다.

### Rationale
1. **K6**: JavaScript 기반 스크립트로 시나리오 작성 용이, Docker 컨테이너로 실행 가능
2. **Prometheus**: Spring Boot Actuator + Micrometer와 자연스러운 통합
3. **Grafana**: K6 + Prometheus 메트릭 시각화, 사전 구축된 Dashboard 활용

### Custom Metrics (Micrometer)

| Metric | 타입 | 설명 |
|--------|------|------|
| `proximity.search.query.duration` | Histogram | ST_DWithin 쿼리 실행 시간 (ms 단위, p50/p95/p99 버킷) |
| `proximity.search.result.count` | Summary | 검색 결과 건수 (평균, 최대) |
| `proximity.cache.hit.ratio` | Gauge | Grid 캐시 적중률 (0.0~1.0) |
| `proximity.search.radius` | Histogram | 요청 반경별 분포 (0.5/1/2/5/20km 버킷) |

### Grafana Dashboard 패널

1. **ST_DWithin 쿼리 Latency** — p50/p95/p99 시계열 그래프, 데이터 건수 오버레이
2. **데이터 규모별 쿼리 성능 변화** — 100K → 500K → 1M 행 증가 시 Latency 추이
3. **Cache Hit Ratio 실시간 추이** — Grid 캐시 적중률 시계열
4. **GIST 인덱스 크기 모니터링** — `pg_relation_size('idx_business_location')` 주기적 수집

### 테스트 시나리오

**기본 시나리오**:
- 검색 QPS 290 → 500 → 1,000 단계별 증가
- 핫스팟 시뮬레이션: 서울 강남 좌표 집중 요청
- 혼합 시나리오: 검색 80% + 상세 조회 15% + 쓰기 5%

**데이터 규모별 성능 프로파일링**:

| 데이터 규모 | 목적 | 측정 항목 |
|------------|------|----------|
| 10K rows | Baseline 성능 측정 | ST_DWithin 쿼리 Latency, GIST Index Scan 비율 |
| 100K rows | 중규모 성능 확인 | Index Scan 유지 여부, 메모리 사용량 변화 |
| 500K rows | 대규모 성능 확인 | Latency 증가율, Seq Scan 전환점 탐색 |
| 1M rows | 목표 규모 성능 검증 | p95/p99 Latency, GIST 인덱스 크기, 캐시 효과 측정 |

**GIST Index Scan vs Sequential Scan 전환점 식별**: `EXPLAIN ANALYZE`로 데이터 규모별 쿼리 플랜을 비교하여, Seq Scan으로 전환되는 임계점과 반경 크기를 확인한다.

---

## Decision 6: Frontend — Vanilla HTML/JS SPA + Leaflet/OpenStreetMap

### Decision
별도 프레임워크 없이 Vanilla HTML/CSS/JavaScript로 1페이지 웹 앱을 구현하며, 지도 시각화를 위해 Leaflet + OpenStreetMap 타일을 사용한다.

### Rationale
1. **프론트엔드는 시스템 설계 학습의 부수적 요소**이나, 위치 기반 서비스의 핵심 UX(지도 마커, 반경 Circle, 좌표 입력)가 텍스트 리스트만으로는 불완전하다.
2. **Leaflet 채택 근거**: 경량(42KB gzipped), CDN 로드, 무료, API 키 불필요, Vanilla JS와 완벽 호환. 빌드 도구 없이 `<script>` 태그로 로드 가능.
3. **OpenStreetMap 채택 근거**: 무료 타일 서버, API 키 불필요, 학습 프로젝트에 비용 부담 제로. 상용 서비스와 달리 과금이나 사용량 제한 없음.
4. 빌드 도구 불필요, Docker에서 Nginx static serving으로 충분

### CDN 로드 방식

```html
<!-- Leaflet CSS -->
<link rel="stylesheet"
      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
      crossorigin="" />

<!-- Leaflet JS -->
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
        integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
        crossorigin=""></script>
```

SRI(Subresource Integrity) hash를 포함하여 CDN 공급망 공격을 방지한다.

### Alternatives Considered

| 대안 | 장점 | 선택하지 않은 이유 |
| ---- | ---- | ------------------ |
| **Google Maps** | 가장 상세한 지도 데이터, 익숙한 UX | API 키 필수, 무료 티어 제한 후 과금, 학습 프로젝트에 부적합 |
| **Mapbox GL JS** | WebGL 기반 고성능 렌더링, 커스텀 스타일 | API 키 필수, 월 50,000회 무료 후 과금 |
| **Naver Maps** | 한국 내 가장 상세한 지도 데이터 | API 키 필수, 국내 전용 서비스, 글로벌 확장 불가 |
| **Kakao Maps** | 한국 내 상세 데이터, 로드뷰 지원 | API 키 필수, 국내 전용 서비스, 일일 호출 제한 |
| **OpenLayers** | 무료, 기능 풍부, GIS 전문 도구 | 번들 크기 크고(~400KB), 학습 곡선 높음, 단순 마커/Circle에 과잉 |

### Trade-off 요약
OpenStreetMap 타일은 한국 지도의 상세도(건물명, 도로명 등)가 Naver/Kakao 대비 낮다. 그러나 이 프로젝트의 핵심은 **좌표 기반 마커 배치, 반경 Circle 시각화, Geohash 셀 경계 Rectangle 표시**이므로 타일 상세도는 무관하다. API 키 없이 즉시 사용 가능한 점이 학습 프로젝트에 더 큰 가치를 제공한다.

---

## Decision 7: CQRS — jOOQ (Read) + JPA (Write)

### Decision
읽기 쿼리는 jOOQ, 쓰기 연산은 Spring Data JPA로 분리한다 (CQRS 패턴).

### Rationale
1. **읽기 쿼리 복잡도**: 공간 검색은 `ST_DWithin`, `ST_Distance` 등 PostGIS 함수 조합이 필요하다. jOOQ의 type-safe SQL DSL은 이러한 복합 쿼리를 Java 코드 레벨에서 안전하게 작성할 수 있다.
2. **컴파일 타임 검증**: JPA의 `@Query` native query는 문자열 기반으로 컴파일 타임에 SQL 오류를 감지할 수 없다. jOOQ는 코드 생성 기반으로 테이블명, 컬럼명, 타입을 컴파일 타임에 검증한다.
3. **PostGIS 함수 바인딩**: jOOQ에서 `ST_DWithin`, `ST_Distance`를 커스텀 함수로 바인딩하여 type-safe하게 호출할 수 있다.
4. **쓰기 경로 JPA 유지**: 엔티티 상태 관리(dirty checking, cascade, lifecycle callback)는 JPA가 적합하다. 쓰기 경로에서 jOOQ를 사용하면 이러한 ORM 기능을 수동 관리해야 한다.

### 구현 방식

```
읽기 경로 (jOOQ):
SearchPort (Outbound Port) → JooqSearchAdapter
BusinessReadPort (Outbound Port) → JooqBusinessReadAdapter
DataSource: Replica (읽기 전용)

쓰기 경로 (JPA):
BusinessWritePort (Outbound Port) → JpaBusinessWriteAdapter
DataSource: Primary (쓰기)
```

- jOOQ code generation: `org.jooq:jooq-codegen` + Flyway 마이그레이션 후 자동 생성
- 생성 코드 위치: `adapter/out/persistence/jooq/` 패키지
- jOOQ DSLContext 설정: `JooqConfig.java`에서 Replica DataSource 바인딩

### jOOQ 검색 쿼리 예시

```java
dslContext
    .select(
        BUSINESSES.ID,
        BUSINESSES.NAME,
        BUSINESSES.ADDRESS,
        stDistance(BUSINESSES.LOCATION, stMakePoint(lng, lat)).as("distance")
    )
    .from(BUSINESSES)
    .where(stDWithin(BUSINESSES.LOCATION, stMakePoint(lng, lat), radiusMeters))
    .orderBy(field("distance"))
    .limit(size)
    .offset(page * size)
    .fetchInto(BusinessSummary.class);
```

### Alternatives Considered

| 대안 | 장점 | 선택하지 않은 이유 |
| ---- | ---- | ------------------ |
| **JPA native query only** | 단일 기술 스택, 학습 곡선 낮음 | 문자열 기반 SQL로 PostGIS 함수 호출이 장황하고, 컴파일 타임 타입 안전성 없음 |
| **jOOQ only (JPA 제거)** | 전체 SQL 제어, 일관된 기술 스택 | 쓰기 경로에서 엔티티 라이프사이클(dirty checking, cascade) 관리가 번거로움 |
| **QueryDSL** | JPA와 자연스러운 통합 | PostGIS 공간 함수 지원이 제한적이며, `ST_DWithin` 등 커스텀 함수 바인딩이 불편 |

### Trade-off 요약
jOOQ 도입으로 기술 스택이 JPA + jOOQ 이중 구조가 되어 학습 비용과 설정 복잡도가 증가한다. 그러나 Read:Write 2500:1 환경에서 읽기 쿼리의 비중이 압도적이므로, 읽기 경로의 타입 안전성과 가독성 향상이 추가 복잡도를 정당화한다.
