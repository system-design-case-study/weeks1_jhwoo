# 주변 사업장 검색 서비스 (Proximity Service) — 시스템 설계 정리

> **참고 도서**: 가상 면접 사례로 배우는 대규모 시스템 설계 기초 2 — 1장 (근접성 서비스)
>
> **프로젝트 기간**: 2026-01-30 ~ 2026-02-10 (12일)
>
> **기술 스택**: Java 21, Spring Boot 3.x, PostgreSQL 16 + PostGIS 3.4, Redis 7, jOOQ, Flyway

---

## 1. 문제 정의

사용자의 현재 위치를 기반으로 **반경 내 사업장을 검색**하고, **사업장 상세 정보를 조회**하며, **사업장 소유주가 정보를 관리**할 수 있는 서비스를 설계한다.

핵심 질문: _"내 주변 1km 이내에 어떤 가게가 있는가?"_

---

## 2. 요구사항 분석

### 2.1 기능적 요구사항

| ID | 요구사항 |
|----|----------|
| FR-001 | 위도, 경도, 반경을 입력받아 범위 내 사업장 목록 반환 |
| FR-002 | 반경 선택지: 0.5km, 1km, 2km, 5km, 20km |
| FR-003 | 검색 결과에 사업장 이름, 주소, 거리(사용자 기준) 포함 |
| FR-004 | 사용자의 명시적 요청에 의해서만 검색 실행 (자동 갱신 없음) |
| FR-005 | 사업장 상세 정보 조회 (영업시간, 사진, 주소 등) |
| FR-006 | 인증된 소유주의 사업장 CRUD |
| FR-007 | 사업장 정보 변경 후 최대 24시간 이내 검색 결과 반영 |
| FR-008 | 좌표 유효성 검증 (위도 -90~90, 경도 -180~180) |
| FR-009 | 비소유주의 수정/삭제 요청 거부 (403) |
| FR-010 | 검색 결과는 거리순 정렬 |

### 2.2 비기능적 요구사항 (성공 기준)

| ID | 기준 | 목표 |
|----|------|------|
| SC-001 | 검색 응답 시간 | p95 < 1초 |
| SC-002 | 동시 사용자 처리 | 1,000명 이상 성능 저하 없이 처리 |
| SC-003 | 데이터 반영 | 변경 후 24시간 이내 99% 반영 |
| SC-004 | 상세 조회 응답 시간 | p95 < 0.5초 |
| SC-005 | 거리 정확도 | 실제 거리 대비 &plusmn;5% |
| SC-006 | 피크 트래픽 응답 | p99 < 2초 |

---

## 3. 규모 추정 (Scale Estimation)

### 3.1 전제 조건

- DAU: 1,000,000명
- 등록 사업장: 1,000,000개
- 1인당 하루 검색 5회
- 검색 결과 중 20% 상세 조회 클릭
- 전체 사업장의 1%가 하루 1회 변경

### 3.2 QPS 산출

| 지표 | 산출 근거 | 값 |
|------|-----------|-----|
| 일 검색 요청 | 1M x 5회 | 5,000,000 |
| 평균 검색 QPS | 5M / 86,400 | ~58 |
| **피크 검색 QPS** | 평균 x 5 | **~290** |
| 일 상세 조회 | 검색당 20건 x 20% = 4건/검색 x 5M | 20,000,000 |
| 평균 상세 조회 QPS | 20M / 86,400 | ~231 |
| **피크 상세 조회 QPS** | 평균 x 5 | **~1,157** |
| 일 쓰기 요청 | 1M x 1% x 1회 | 10,000 |
| 평균 쓰기 QPS | 10K / 86,400 | ~0.12 |
| **읽기:쓰기 비율** | (5M + 20M) : 10K | **~2,500 : 1** |

### 3.3 규모 추정에서 도출한 설계 방향

- **Read 압도적 우세 (2,500:1)** → 읽기 경로 최적화 집중, Read Replica 활용, 캐시 적극 도입
- **쓰기 QPS 극히 낮음 (~0.12)** → GIST 인덱스 유지 비용 무시 가능, 캐시 무효화 부하도 무시 가능
- **피크 읽기 QPS ~1,450** → PostgreSQL Replica로 충분히 처리 가능하나, Latency 목표를 위해 Redis 캐시 추가
- **24시간 Eventual Consistency** → 비동기 Replication과 TTL 캐시로 구현 가능

---

## 4. 설계 결정 (Design Decisions)

모든 기술 결정에 **2개 이상 대안을 비교**하고, "선택하지 않은 이유"를 명시했다.

### 4.1 공간 검색 알고리즘 — PostGIS `ST_DWithin`

**결정**: PostGIS의 `ST_DWithin` + GIST 인덱스를 primary 검색 알고리즘으로 채택

**핵심 이유**:
- 정확한 반경 검색 (셀 경계 문제 없음)
- GIST 인덱스 기반 100만 건 기준 15~60ms 쿼리 성능
- 후처리 필터링 불필요 — 단일 쿼리로 거리 계산, 반경 필터, 정렬 모두 처리

실제 구현된 jOOQ 쿼리 (`JooqSearchAdapter.java`):

```java
Field<Object> point = stMakePoint(lng, lat);
Condition where = stDWithin(BUSINESSES.LOCATION, point, radiusMeters);
if (category != null) {
    where = where.and(BUSINESSES.CATEGORY.eq(category));
}

dsl.select(
        BUSINESSES.ID, BUSINESSES.NAME, BUSINESSES.ADDRESS,
        BUSINESSES.LATITUDE, BUSINESSES.LONGITUDE,
        stDistance(BUSINESSES.LOCATION, point).as("distance"),
        BUSINESSES.CATEGORY
    )
    .from(BUSINESSES)
    .where(where)
    .orderBy(DSL.field("distance"))
    .limit(size)
    .offset(page * size)
    .fetchInto(BusinessSummary.class);
```

생성되는 SQL (카테고리 필터 적용 시):

```sql
SELECT id, name, address, latitude, longitude,
       ST_Distance(location, ST_MakePoint(?, ?)::geography) AS distance,
       category
FROM businesses
WHERE ST_DWithin(location, ST_MakePoint(?, ?)::geography, ?)
  AND category = ?
ORDER BY distance
LIMIT ? OFFSET ?
```

별도로 전체 결과 수를 조회하는 `countByLocation()` 메서드도 구현되어 있다:

```sql
SELECT COUNT(*) FROM businesses
WHERE ST_DWithin(location, ST_MakePoint(?, ?)::geography, ?)
  AND category = ?
```

**대안 비교**:

| 대안 | 장점 | 선택하지 않은 이유 |
|------|------|-------------------|
| **Geohash (B-tree)** | 캐싱 키가 자연스러움, MySQL 호환 | 셀 경계 문제로 인접 셀 추가 조회 필수, 후처리 필터링 필요 |
| **Quadtree** | 데이터 밀도 adaptive 분할 | RDB에 직렬화 비효율적, 메모리 기반 구조라 Stateless 원칙 위배 |
| **Query Hash (좌표+반경 MD5)** | 구현 단순 | 미세 좌표 차이도 캐시 미스 유발, 적중률 극히 낮음 |

### 4.2 데이터베이스 — PostgreSQL 16 + PostGIS 3.4 + Streaming Replication

**결정**: PostgreSQL + PostGIS, 비동기 Streaming Replication으로 Primary 1 + Replica 2 구성

```
              +----------+
   Writes --> | Primary  |
              +----+-----+
                   | Streaming Replication (async)
          +--------+--------+
          v                 v
   +----------+      +----------+
   | Replica 1|      | Replica 2|
   +----------+      +----------+
          ^                 ^
          +--------+--------+
                   |
              Load Balancer
                   |
              Reads (LBS)
```

**핵심 이유**:
- PostGIS의 `ST_DWithin`, GIST 인덱스, `GEOGRAPHY` 타입이 공간 검색에 필수
- 24시간 Eventual Consistency 윈도우가 있으므로 비동기 Replication의 수 초~수 분 lag 허용
- `AbstractRoutingDataSource`로 `@Transactional(readOnly = true)` 기반 자동 라우팅

실제 구현 (`DataSourceConfig.java`):

```java
AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
    @Override
    protected Object determineCurrentLookupKey() {
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return readOnly ? REPLICA : PRIMARY;
    }
};
// LazyConnectionDataSourceProxy로 감싸 트랜잭션 시작까지 커넥션 획득 지연
return new LazyConnectionDataSourceProxy(routingDataSource);
```

**대안 비교**:

| 대안 | 선택하지 않은 이유 |
|------|-------------------|
| MySQL 8.0 | PostGIS의 GIST 인덱스, `GEOGRAPHY` 타입 미지원 |
| 단일 PostgreSQL | 단일 장애점(SPOF), 피크 시 성능 불안 |
| Replica 3대 이상 | 현재 QPS에서 과잉, 운영 비용 증가 |

### 4.3 캐싱 — Redis + Grid-based Cache Key

**결정**: 좌표를 **0.01도 단위로 반올림**하여 deterministic 캐시 키 생성

실제 구현 (`GridCacheKeyGenerator.java`):

```java
private static final double GRID_SIZE = 0.01;

public static String searchKey(double lat, double lng, double radiusKm,
                                String category, int page, int size) {
    double roundedLat = roundToGrid(lat);  // Math.round(lat / 0.01) * 0.01
    double roundedLng = roundToGrid(lng);
    String cat = (category != null) ? category : "ALL";
    return "search:" + roundedLat + ":" + roundedLng + ":" + radiusKm
            + ":" + cat + ":" + page + ":" + size;
}
```

**Grid 크기별 캐시 히트율 시뮬레이션**:

| Grid 크기 | 셀 크기 (at 37 N) | 예상 히트율 | 평가 |
|-----------|-------------------|-----------|------|
| 0.001도 | ~111m x 88m | 5~15% | 미세 차이로 캐시 미스 빈번 |
| **0.01도** | **~1.1km x 0.88km** | **60~80%** | **채택 — 히트율과 정밀도 균형** |
| 0.05도 | ~5.5km x 4.4km | 85~95% | 히트율 높으나 검색 정밀도 저하 |

**캐시 전략** (실제 `RedisCacheAdapter.java`에서 확인):

| 항목 | 검색 결과 | 상세 정보 |
|------|----------|----------|
| Key | `search:{lat}:{lng}:{radius}:{category}:{page}:{size}` | `business:{id}` |
| TTL | 10분 (`SEARCH_CACHE_TTL`) | 30분 (`BUSINESS_CACHE_TTL`) |

**Cache Invalidation** (실제 `RedisCacheAdapter.invalidateSearchCache()`):
- **Time-based**: TTL 만료 시 자동 삭제
- **Event-driven**: 사업장 변경 시 `adjacentSearchKeyPatterns()`로 3x3 Grid(9개 셀) 캐시 삭제
- Write QPS ~0.12로 invalidation 부하 무시 가능

**Privacy by Design**:
- `Math.round(lat / 0.01) * 0.01` 연산은 **비가역적** — 반올림된 좌표에서 원본 좌표 복원 불가
- 캐시 키만으로 개별 사용자의 정확한 위치 추론 불가 (~1.1km 단위)

**대안 비교**:

| 대안 | 선택하지 않은 이유 |
|------|-------------------|
| 캐시 미사용 | Latency 목표(p95 <1초) 여유 부족, 피크 대응 불안 |
| Local Cache (Caffeine) | Stateless 원칙 위배, 인스턴스 간 불일치 |
| Redis Geospatial (GEOADD) | 1M 데이터 전체 Redis 적재 필요, DB와 이중 관리 복잡 |

### 4.4 아키텍처 — Hexagonal Architecture (Port/Adapter)

**결정**: 도메인 로직과 인프라를 Port/Adapter 패턴으로 분리

실제 구현된 구조:

```
adapter/in/web/              (Inbound Adapter)
  SearchController           -> SearchUseCase (Port/In)
  BusinessController         -> BusinessUseCase (Port/In)
  OwnerController            -> OwnerAuthUseCase (Port/In)
  CategoryController         -> CategoryUseCase (Port/In)
  JwtAuthenticationFilter
  RateLimitFilter
  GlobalExceptionHandler

application/port/out/        (Outbound Port - 인터페이스)
  SearchPort                 -> JooqSearchAdapter
  BusinessReadPort           -> JooqBusinessReadAdapter
  BusinessWritePort          -> JpaBusinessWriteAdapter
  CachePort                  -> RedisCacheAdapter
  AuthPort                   -> JwtAuthAdapter
  OwnerPort                  -> JpaOwnerAdapter
  CategoryPort               -> JooqCategoryAdapter

domain/                      (순수 도메인 - 외부 의존성 없음)
  Business, BusinessHours, BusinessPhoto, Owner
```

**핵심 이유**:
- Domain이 인프라에 의존하지 않으므로 단위 테스트가 간단 (Mock으로 Port 대체)
- PostGIS → 다른 공간 DB 전환 시 Adapter만 교체
- 검색(SearchUseCase)과 사업장 관리(BusinessUseCase)의 관심사 명확 분리

### 4.5 CQRS — jOOQ (Read) + JPA (Write)

**결정**: 읽기는 jOOQ type-safe DSL, 쓰기는 JPA ORM으로 분리

실제 구현:

```
읽기 경로 (jOOQ + Replica):
  SearchPort       -> JooqSearchAdapter        (ST_DWithin 검색 + countByLocation)
  BusinessReadPort -> JooqBusinessReadAdapter   (상세 조회)
  CategoryPort     -> JooqCategoryAdapter       (DISTINCT 카테고리 목록)

쓰기 경로 (JPA + Primary):
  BusinessWritePort -> JpaBusinessWriteAdapter  (CRUD)
  OwnerPort         -> JpaOwnerAdapter          (회원가입/인증)
```

`PostgisFunction.java`에서 `stMakePoint`, `stDWithin`, `stDistance`를 jOOQ `Field`/`Condition`으로 래핑하여, PostGIS 함수를 type-safe하게 호출한다.

**대안 비교**:

| 대안 | 선택하지 않은 이유 |
|------|-------------------|
| JPA native query만 사용 | 문자열 기반 SQL, 컴파일 타임 안전성 없음 |
| jOOQ만 사용 (JPA 제거) | 쓰기 경로에서 dirty checking, cascade 등 ORM 기능 수동 관리 필요 |
| QueryDSL | PostGIS 공간 함수 지원 제한적 |

### 4.6 프론트엔드 — Vanilla JS + Leaflet/OpenStreetMap

**결정**: 프레임워크 없이 Vanilla HTML/CSS/JS + Leaflet + MarkerClusterGroup

**핵심 이유**:
- 프론트엔드는 시스템 설계 학습의 부수적 요소
- 위치 기반 서비스의 핵심 UX(지도 마커, 반경 Circle, 좌표 입력)는 텍스트만으로 불완전
- Leaflet: 경량, API 키 불필요, Vanilla JS 호환
- OpenStreetMap: 무료, API 키 불필요, 과금/사용량 제한 없음

**대안 비교**:

| 대안 | 선택하지 않은 이유 |
|------|-------------------|
| Google Maps | API 키 필수, 무료 티어 제한 후 과금 |
| Mapbox GL JS | API 키 필수, 월 50,000회 무료 후 과금 |
| Naver/Kakao Maps | API 키 필수, 국내 전용, 일일 호출 제한 |

### 4.7 카테고리 기반 검색 — 서버사이드 필터링 + 동적 카테고리 관리

**결정**: 초기 화면에 Yelp 스타일 카테고리 카드를 표시하고, 카테고리 선택이 곧 검색 트리거가 되는 UX로 설계

**핵심 이유**:
- 기존 UX: 빈 화면 → 검색 버튼 클릭 필요 → 사용자 첫 행동이 불명확
- 카테고리를 먼저 보여주면 사용자가 즉시 행동 가능
- 카테고리 필터를 서버사이드로 전환하여 페이지네이션과 정합성 보장

**Backend 구현**:

새 엔드포인트 `GET /api/categories`:
```java
@Service
public class CategoryService implements CategoryUseCase {
    private static final String CACHE_KEY = "categories";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    @Override
    public List<String> getCategories() {
        // Redis 캐시 우선 조회, 미스 시 DB fallback
    }

    @Scheduled(fixedRate = 12, timeUnit = TimeUnit.HOURS)
    public void refreshCategories() {
        List<String> categories = categoryPort.findDistinctCategories();
        redisTemplate.opsForValue().set(CACHE_KEY, categories, CACHE_TTL);
    }

    @PostConstruct
    void initCategories() { refreshCategories(); }
}
```

생성 SQL:
```sql
SELECT DISTINCT category FROM businesses WHERE category IS NOT NULL ORDER BY category
```

**검색 API 변경**:
- `SearchRequest`에 `category` 필드 추가 (Optional, null이면 전체 검색)
- `size` 기본값 20 → **200**, 최대값 50 → **200** (카테고리 + 무한 스크롤 대응)
- `SearchPort.countByLocation()` 추가 — 전체 결과 수 반환 (무한 스크롤의 총 페이지 계산)

**캐시 키 변경**:
- 기존: `search:{lat}:{lng}:{radius}:{page}:{size}`
- 변경: `search:{lat}:{lng}:{radius}:{category}:{page}:{size}` (category 없으면 `ALL`)

### 4.8 무한 스크롤 — IntersectionObserver + Backend 페이지네이션

**결정**: 검색 반경이 클 때 (5~20km) 대량 결과를 IntersectionObserver 기반 무한 스크롤로 처리

**핵심 이유**:
- 5km 반경 강남 검색 시 5,000건 이상 결과 발생
- 기존 `size=200` 고정으로는 가까운 200건만 표시 가능
- Backend의 `page`, `size` 파라미터를 활용하여 추가 구현 없이 페이지네이션 지원

**설계 원칙**:
- **지도 마커**: 첫 페이지(200건)만 표시하여 렌더링 성능 유지
- **리스트**: 스크롤 시 다음 페이지를 자동 로드하여 전체 결과 탐색 가능
- **키워드 필터**: 누적된 전체 결과(`allResults`) 대상으로 클라이언트 필터링

**Frontend 구현** (`search.js`):
```javascript
const PAGE_SIZE = 200;
let currentPage = 0, totalCount = 0, isLoading = false;

function setupScrollObserver() {
    scrollObserver = new IntersectionObserver(entries => {
        if (entries[0].isIntersecting && !isLoading && hasMore()) loadMore();
    });
    scrollObserver.observe(document.getElementById('scroll-sentinel'));
}

async function loadMore() {
    isLoading = true;
    currentPage++;
    const result = await Api.search(lat, lng, radius, category, currentPage, PAGE_SIZE);
    allResults = allResults.concat(result.data.businesses);
    appendResultList(result.data.businesses);
    updateSentinelVisibility();
    isLoading = false;
}
```

### 4.9 Viewport 검색 시도와 철회

프로젝트 과정에서 Viewport(Bounding Box) 기반 검색을 구현한 후 **전량 철회**했다.

**시도 이유**: 20km 반경에서 수만 건 결과의 멀티 페이지 병렬 로드로 429 Rate Limit 발생

**구현 내용**: `GET /api/search/bounds` 엔드포인트 + `ST_MakeEnvelope` + `&&` 연산자 기반 Bounding Box 검색 + Frontend `moveend` 이벤트 기반 자동 재검색

**철회 이유**:
- 넓은 줌에서 수천 건 조회로 오히려 성능 문제 발생
- "내 위치" 기준 → "화면" 기준으로 바뀌면서 **근접성 서비스의 정체성 상실**
- 지도 이동 시 자동 재검색으로 불필요한 API 호출 폭증
- 반경 선택이 줌 프리셋으로 전락하여 의미 없는 UX

**교훈**: 기능을 추가하기 전에 "이 기능이 서비스의 본질적 가치와 부합하는가"를 먼저 검증해야 한다. 근접성 서비스의 핵심 질문은 _"내 주변에 뭐가 있는가"_ 이지, _"화면에 보이는 곳에 뭐가 있는가"_ 가 아니다.

---

## 5. 시스템 아키텍처

### 5.1 전체 아키텍처

```
[Client (Browser)]
  +-- Leaflet Map (MarkerClusterGroup, Circle, Popup)
  +-- Side Panel (검색/즐겨찾기/관리 모드 전환)
  +-- Browser Geolocation API (fallback: 서울 시청)
       |
       v
[Nginx — 정적 파일 서빙 + API Reverse Proxy + Rate Limiting]
       |
       +-- /api/search ---------> [SearchController]
       |                               |
       |                               v
       |                         [SearchService]
       |                          |         |
       |                     CachePort    SearchPort (jOOQ)
       |                      (Redis)    (PostgreSQL Replica)
       |
       +-- /api/businesses/{id} -> [BusinessController]
       |                               |
       |                               v
       |                         [BusinessService]
       |                          |         |
       |                     CachePort    BusinessReadPort (jOOQ)
       |                      (Redis)    (PostgreSQL Replica)
       |
       +-- /api/businesses (CUD) -> [BusinessController]
       |                                 |
       |                                 v
       |                           [BusinessService]
       |                            |    |    |
       |                       AuthPort  |  CachePort
       |                        (JWT) WritePort (Invalidate 3x3 Grid)
       |                             (JPA + Primary)
       |
       +-- /api/categories -----> [CategoryController]
       |                               |
       |                               v
       |                         [CategoryService]
       |                          |         |
       |                     CachePort    CategoryPort (jOOQ)
       |                      (Redis)    (PostgreSQL Replica)
       |
       +-- /api/owners ----------> [OwnerController]
                                        |
                                        v
                                   [OwnerAuthService]
                                    |         |
                                OwnerPort   AuthPort
                               (JPA + Primary) (JWT)
```

### 5.2 Request Flow (검색)

```
1. Client: 카테고리 카드 "카페" 클릭
   -> Nginx (Rate Limit 60req/min)
   -> GET /api/search?lat=37.49&lng=127.02&radius=1&category=카페&size=200

2. SearchController -> SearchService.search(request)

3. SearchService:
   a. 반경 검증: ALLOWED_RADII_KM = {0.5, 1.0, 2.0, 5.0, 20.0}
   b. Grid 반올림: lat=37.4979 -> 37.50, lng=127.0276 -> 127.03
   c. 캐시 키 생성: "search:37.50:127.03:1.0:카페:0:200"
   d. Redis 조회 (Cache Hit) -> 즉시 반환
   e. Redis 미스 (Cache Miss):
      - JooqSearchAdapter.searchByLocation(category=카페) -> Replica DB
      - PostGIS ST_DWithin + GIST Index Scan + category 필터
      - JooqSearchAdapter.countByLocation() -> 전체 결과 수 조회
      - SearchResponse(businesses, total, page, size) 생성
      - Redis에 저장 (TTL 10분)
      - 반환

4. Client: 결과를 MarkerClusterGroup 마커 + 사이드 리스트로 렌더링
   - 마커: 첫 페이지(200건)만 표시
   - 리스트: 스크롤 시 다음 페이지 자동 로드 (무한 스크롤)
```

### 5.3 Container Topology (Docker Compose)

| 컨테이너 | 역할 | 포트 |
|----------|------|------|
| proximity-nginx | 정적 파일 서빙 + API Reverse Proxy + Rate Limiting | 80 |
| proximity-backend | Spring Boot API | 8080 |
| proximity-postgres-primary | PostgreSQL Primary (Write) | 5432 |
| proximity-postgres-replica-1 | Read Replica | 5433 |
| proximity-postgres-replica-2 | Read Replica | 5434 |
| proximity-redis | Redis 캐시 | 6379 |
| proximity-prometheus | Metrics 수집 | 9090 |
| proximity-grafana | Dashboard 시각화 | 3000 |
| proximity-csv-loader | 실제 음식점 CSV 데이터 적재 (seed profile) | - |

기동 순서: `postgres-primary (healthy) -> replica-1, replica-2 (healthy) -> redis (healthy) -> backend -> nginx`

---

## 6. 데이터 모델

### 6.1 Entity Relationship

```
+----------+        +--------------+
|  Owner   | 1---N  |   Business   |
|          |        |              |
| id (PK)  |        | id (PK)      |
| email    |        | owner_id(FK) |
| password |        | name         |
| name     |        | address      |
|          |        | latitude     |
|          |        | longitude    |
|          |        | location     |  <-- GEOGRAPHY(Point, 4326)
|          |        | phone        |
|          |        | category     |
+----------+        +------+-------+
                           |
                    +------+-------+
                    |              |
               1---N|         1---N|
        +-----------+--+  +-------+--------+
        |BusinessHours |  | BusinessPhoto  |
        | day_of_week  |  | photo_url      |
        | open_time    |  | display_order  |
        | close_time   |  |                |
        | is_closed    |  |                |
        +--------------+  +----------------+
```

### 6.2 핵심 인덱스

| 인덱스 | 타입 | 용도 |
|--------|------|------|
| `idx_business_location` | **GIST** | `ST_DWithin` 공간 검색 최적화 |
| `idx_owner_id` | B-tree | 소유주별 사업장 조회 |
| `UNIQUE(owner_id, name, latitude, longitude)` | B-tree | 중복 등록 방지 |

### 6.3 Trigger — location 자동 계산

`latitude`, `longitude` 변경 시 `location` 컬럼(GEOGRAPHY)을 자동으로 갱신:

```sql
CREATE TRIGGER trg_business_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON businesses
    FOR EACH ROW EXECUTE FUNCTION update_business_location();
    -- NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
```

### 6.4 실제 데이터 적재 — 식품 일반음식점 CSV

랜덤 시드가 아닌 **공공데이터 포털의 식품 일반음식점 CSV**를 사용한다.

Python 스크립트(`scripts/load-csv-data.py`)가 CSV를 파싱하여 DB에 적재:
- 좌표 변환: EPSG:5174 (한국 TM 좌표계) → EPSG:4326 (WGS84) (`pyproj` 사용)
- 인코딩: CP949
- 영업 중인 사업장만 필터링 (`영업상태명 = "영업/정상"`)
- 배치 INSERT (`psycopg2.extras.execute_values`, 5,000건 단위)
- 중복 방지: `ON CONFLICT (owner_id, name, latitude, longitude) DO NOTHING`

Docker Compose의 `csv-loader` 서비스로 자동 적재 가능:

```bash
docker compose --profile seed up csv-loader
```

---

## 7. 확장 전략

### 7.1 현재 구성의 확장 포인트

| 전략 | 구현 방식 | 확장 시나리오 |
|------|----------|-------------|
| **Read/Write 분리** | `AbstractRoutingDataSource` + `@Transactional(readOnly)` | Replica 추가만으로 읽기 처리량 선형 증가 |
| **Stateless 서비스** | 세션 상태 없음, JWT 인증 | Backend 인스턴스 수평 확장 (Nginx 뒤 N대) |
| **Redis 캐시** | 검색 10분 TTL, 상세 30분 TTL | 캐시 적중 시 DB 접근 완전 회피 |
| **Nginx Load Balancing** | `upstream backend` Round-robin | upstream 서버 추가로 부하 분산 |

### 7.2 트래픽 증가 시 단계별 대응

```
현재 (피크 ~1,450 QPS)
  |
  +-- 1단계: Redis 캐시로 DB 접근 60~80% 감소
  |
  +-- 2단계: Replica 추가 (2대 -> 4대)로 나머지 읽기 분산
  |
  +-- 3단계: Backend 인스턴스 추가 (Stateless라 즉시 확장)
  |
  +-- 4단계 (미래): Redis Cluster, DB 파티셔닝
```

### 7.3 Read:Write 2,500:1이 설계에 미친 영향

- **GIST 인덱스 유지 비용**: 쓰기가 ~0.12 QPS이므로 인덱스 갱신 부담이 사실상 없다
- **비동기 Replication**: 쓰기 빈도가 낮아 Replication Lag가 극히 짧다
- **캐시 무효화 빈도**: 하루 10,000건 변경 → 분당 ~7건의 캐시 무효화, 부하 무시 가능
- **TTL 기반 Lazy Invalidation**: 24시간 Consistency 윈도우 + 낮은 쓰기 빈도 → TTL만으로 충분

---

## 8. 부하 테스트

### 8.1 도구 — k6 + Prometheus + Grafana

| 도구 | 역할 |
|------|------|
| **k6** | JavaScript 기반 부하 테스트, Docker 실행 가능 |
| **Prometheus** | Spring Boot Actuator + Micrometer 메트릭 수집 |
| **Grafana** | 실시간 Dashboard 시각화 |

### 8.2 테스트 시나리오

**검색 부하 테스트 (`k6/search-load-test.js`)**:

트래픽 패턴 (6분):
```
290 req/s (1분) -> 500 req/s (2분) -> 1000 req/s (2분) -> 290 req/s (1분)
```

요청 비율:
- 80% 검색 (`GET /api/search`) — 30% 강남 핫스팟, 70% 전국 랜덤
- 15% 상세 조회 (`GET /api/businesses/{id}`)
- 5% 쓰기 (`POST /api/businesses`)

**데이터 규모별 성능 프로파일링 (`k6/data-scale-test.js`)**:

| 데이터 규모 | 목적 |
|------------|------|
| 10K rows | Baseline 성능 측정 |
| 100K rows | 중규모 — Index Scan 유지 여부 확인 |
| 500K rows | 대규모 — Latency 증가율, Seq Scan 전환점 탐색 |
| 1M rows | 목표 규모 — p95/p99 Latency, GIST 인덱스 크기, 캐시 효과 |

### 8.3 Success Criteria

| 지표 | 목표 |
|------|------|
| 검색 p95 | < 1000ms |
| 검색 p99 (피크) | < 2000ms |
| 에러율 | < 1% |
| 상세 조회 p95 | < 500ms |

---

## 9. 모니터링 (Observability)

### 9.1 Custom Metrics (Micrometer)

`MetricsConfig.java`에서 등록한 4개 Custom Metric:

| Metric | 타입 | 설명 |
|--------|------|------|
| `proximity.search.query.duration` | Timer | ST_DWithin 쿼리 실행 시간 (JooqSearchAdapter에서 기록) |
| `proximity.search.result.count` | DistributionSummary | 검색 결과 건수 |
| `proximity.cache.hit.ratio` | Gauge | Grid 캐시 적중률 (CacheHitRatioHolder로 hit/miss 추적) |
| `proximity.search.radius` | DistributionSummary | 요청 반경별 분포 |

### 9.2 Grafana Dashboard

- Prometheus를 Data Source로 자동 프로비저닝 (`docker/grafana/provisioning/`)
- Dashboard JSON 자동 로드 (`docker/grafana/dashboards/`)

---

## 10. 프론트엔드 상세

프론트엔드는 9개 JS 모듈로 구성:

| 모듈 | 역할 |
|------|------|
| `app.js` | 진입점, 모드 전환 (검색/즐겨찾기/관리), DOMContentLoaded 초기화 |
| `map.js` | Leaflet 초기화, **MarkerClusterGroup**, Circle, 임시 마커, flyTo, 카테고리별 마커 색상 |
| `geolocation.js` | Geolocation API, `isSecureContext` 확인, fallback (서울 시청 37.5665, 126.9780) |
| `api.js` | Backend API fetch 래퍼, JWT 토큰 관리 (localStorage), 에러 메시지 매핑, `getCategories()` |
| `search.js` | **카테고리 카드 UI** (초기 화면), 서버사이드 카테고리 검색, **무한 스크롤**, 키워드 필터, 정렬 |
| `detail.js` | 상세 패널 (영업시간 테이블, 사진, 즐겨찾기 토글), 404 시 리스트에서 자동 제거 |
| `admin.js` | 관리자 모드 — 로그인/회원가입, **지도 클릭 좌표 입력** (crosshair 커서), CRUD 폼 |
| `favorites.js` | **즐겨찾기** + **최근 본 사업장** (localStorage, 최대 20건) |
| `geohash-viz.js` | 지도 위 셀 시각화 토글 (학습 보조) |

### 10.1 Yelp 스타일 카테고리 기반 UX

초기 화면은 빈 검색 결과 대신 **카테고리 카드 그리드**를 표시한다:

```
[초기 화면]
  "근처에 무엇을 찾고 계세요?"
  ┌─────────┐  ┌─────────┐
  │ ☕ 카페  │  │ 🍽️ 식당  │
  └─────────┘  └─────────┘
  ┌─────────┐  ┌─────────┐
  │ 🏪 편의점 │  │ 💊 약국  │
  └─────────┘  └─────────┘
  반경: [1km ▼]

[카테고리 클릭 → 검색 결과]
  [← 뒤로]  카테고리: [카페 ▼]  반경: [1km ▼]
  ☕ 카페 42건
  ───────────────
  스타벅스 강남점  350m
  투썸플레이스     520m
  ...
```

- 카테고리 목록은 `GET /api/categories`로 DB에서 동적 로드
- 카테고리 클릭 → `GET /api/search?category=카페&size=200` (서버사이드 필터링)
- "뒤로" 버튼으로 카테고리 선택 화면 복귀
- 카테고리별 이모지 매핑 (알려진 카테고리만, 나머지 기본 📍 아이콘)
- 카테고리별 마커 색상 (카페: 갈색, 식당: 빨강, 약국: 파랑 등)

### 10.2 무한 스크롤

- IntersectionObserver로 스크롤 센티널(`#scroll-sentinel`) 감시
- 센티널이 화면에 진입하면 다음 페이지 자동 로드 (`currentPage++`)
- `isLoading` 가드로 중복 요청 방지
- 마커는 첫 페이지(200건) 고정, 리스트는 전체 페이지 누적
- 검색 정보 표시: "카페 5,458건 (400개 표시)"

### 10.3 기타 기능

- 즐겨찾기/최근 본 사업장 (localStorage 기반, 별도 백엔드 API 없이 클라이언트에서 처리)
- 키워드 검색 (debounce 300ms, 누적된 전체 결과 대상 클라이언트 필터)
- 거리순/이름순 정렬
- 지도 더블클릭으로 검색 위치 수동 선택 (주황색 마커)
- MarkerClusterGroup으로 마커 밀집 시 클러스터링

---

## 11. 추가 고려사항

### 11.1 Rate Limiting (2단계)

실제 구현된 이중 방어:

| 단계 | 위치 | 제한 | 구현 |
|------|------|------|------|
| 1단계 | Nginx `limit_req_zone` | IP당 60req/min (burst=20, nodelay) | `nginx.conf` |
| 2단계 | Backend `RateLimitFilter` | IP당 300req/min | `ConcurrentHashMap` + 시간 윈도우 |

초과 시: `429 Too Many Requests` + `Retry-After: 60` 헤더 + JSON ErrorResponse

### 11.2 Edge Cases

| EC | 상황 | 실제 구현 |
|----|------|----------|
| EC-1 | 반경 내 사업장 없음 | 빈 리스트 + `200 OK`, 프론트엔드 "반경을 넓혀보세요" 안내 |
| EC-2 | 유효하지 않은 좌표 | `@Valid` Bean Validation + Domain 검증 + DB CHECK |
| EC-3 | 중복 사업장 등록 | `UNIQUE(owner_id, name, lat, lng)` + `409 Conflict` |
| EC-4 | 트래픽 초과 | 2단계 Rate Limiting (Nginx + Backend Filter) |
| EC-5 | 삭제 후 검색 노출 | `Detail.show()` 에서 404 수신 시 `Search.removeFromList()` 호출 |
| EC-6 | Geolocation 실패 | 서울 시청 fallback + 회색 마커 + Toast 안내 |
| EC-7 | HTTPS 미사용 | `window.isSecureContext` 확인 + 동일 fallback |

### 11.3 보안

| 항목 | 구현 |
|------|------|
| 인증 | JWT Bearer Token (`JwtAuthAdapter` + `JwtAuthenticationFilter`) |
| 비밀번호 | BCrypt 해싱 |
| 소유권 검증 | `Business.isOwnedBy()` — 비소유주 `403 Forbidden` |
| API 보호 | Rate Limiting 이중화 (Nginx + Backend) |
| CORS | `SecurityConfig`에서 설정 |

---

## 12. 구현 산출물 요약

| 영역 | 파일 수 | 핵심 내용 |
|------|---------|----------|
| Domain | 4 | Business, BusinessHours, BusinessPhoto, Owner |
| Application | 32 | UseCase 4, Port 7, Service 5, DTO 10, Exception 6 |
| Adapter (Inbound) | 7 | Controller 4 (Search, Business, Owner, Category), JwtFilter, RateLimitFilter, GlobalExceptionHandler |
| Adapter (Outbound) | 10 | jOOQ 3 (Search, BusinessRead, Category), JPA 2, JPA Repository 2, Redis, JWT, PostgisFunction |
| Config | 5 | DataSource, jOOQ, Redis, Security, Metrics |
| Test | 37 파일 / 215개 | Singleton Testcontainer 기반, 도메인/서비스/컨트롤러/인프라/Adapter-Persistence/Cache 통합 테스트 |
| Frontend | 9 JS | app, map, geolocation, api, search, detail, admin, favorites, geohash-viz |
| Infra | Docker Compose (9 서비스), nginx.conf, Flyway, k6 2개, CSV loader |

---

## 13. 회고

### 잘한 점

- **설계 문서 선행 작성**: plan → spec → research → data-model을 구현 전에 작성하여 "왜 이 기술을 선택했는가"에 대한 고민 시간이 크게 줄었다
- **대안 비교 문서화**: 모든 결정에 2개 이상 대안을 비교하고 "선택하지 않은 이유"를 명시한 것이 설계 리뷰에 효과적
- **Hexagonal Architecture**: Port/Adapter 덕분에 Category 검색 추가 시 기존 도메인/인프라 코드에 영향 없이 새 Port/Adapter만 추가
- **실제 데이터 사용**: 랜덤 시드 대신 공공데이터 포털의 실제 음식점 데이터를 적재하여 현실적인 데이터 분포에서 검증
- **Viewport 검색 시도와 빠른 철회**: 구현 후 서비스 본질과 맞지 않음을 인식하고 전량 revert — "작동하는 코드"보다 "올바른 설계"를 우선한 판단
- **테스트 인프라 투자**: Singleton Testcontainer 도입으로 테스트 실행 시간 47초→18초(62% 단축), 152→215개 테스트로 커버리지 강화

### 개선할 점

- **테스트 자동화 시점**: 인프라 통합 테스트를 구현 초기에 작성했으면 설정 오류를 더 빠르게 발견할 수 있었다
- **Grid 캐시 키 부동소수점**: `Math.round(value / 0.01) * 0.01`에서 부동소수점 오차 가능 — 운영 시 `BigDecimal` 또는 정수 기반 Grid 고려 필요
- **부하 테스트 결과 미기록**: k6 스크립트는 작성했으나 실행 결과를 정량적으로 기록하지 않았다
- **Viewport 검색 설계 검증 부재**: 구현 전에 "서비스 본질과 부합하는가"를 설계 단계에서 충분히 검토했으면 구현/철회 비용을 절약할 수 있었다
