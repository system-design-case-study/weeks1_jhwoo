# Proximity Service — 주변 사업장 검색 서비스

> **참고 도서**: 가상 면접 사례로 배우는 대규모 시스템 설계 기초 2 — 1장 (근접성 서비스)
>
> **기술 스택**: Java 21, Spring Boot 3.x, PostgreSQL 16 + PostGIS 3.4, Redis 7, jOOQ, Flyway

사용자의 현재 위치를 기반으로 **반경 내 사업장을 검색**하고, **사업장 상세 정보를 조회**하며, **사업장 소유주가 정보를 관리**할 수 있는 서비스.

핵심 질문: _"내 주변 1km 이내에 어떤 가게가 있는가?"_

---

## 시스템 아키텍처

```
[Client (Browser)]
  +-- Leaflet Map (MarkerClusterGroup, Circle, Popup)
  +-- Side Panel (검색/즐겨찾기/관리 모드 전환)
  +-- Browser Geolocation API (fallback: 서울 시청)
       |
       v
[Nginx — 정적 파일 서빙 + API Reverse Proxy + Rate Limiting (60req/min)]
       |
       +-- /api/search ---------> SearchController → SearchService
       |                            CachePort(Redis) / SearchPort(jOOQ → Replica)
       |
       +-- /api/businesses/{id} -> BusinessController → BusinessService
       |                            CachePort(Redis) / BusinessReadPort(jOOQ → Replica)
       |
       +-- /api/businesses (CUD) -> BusinessController → BusinessService
       |                            AuthPort(JWT) / BusinessWritePort(JPA → Primary)
       |                            CachePort(3x3 Grid Invalidation)
       |
       +-- /api/categories -----> CategoryController → CategoryService
       |                            CachePort(Redis) / CategoryPort(jOOQ → Replica)
       |
       +-- /api/owners ----------> OwnerController → OwnerAuthService
                                    OwnerPort(JPA → Primary) / AuthPort(JWT)
```

### Container Topology (Docker Compose)

| 컨테이너 | 역할 | 포트 |
|----------|------|------|
| `proximity-nginx` | 정적 파일 서빙 + API Reverse Proxy + Rate Limiting | 80 |
| `proximity-backend` | Spring Boot API | 8080 |
| `proximity-postgres-primary` | PostgreSQL Primary (Write) | 5432 |
| `proximity-postgres-replica-1` | Read Replica | 5433 |
| `proximity-postgres-replica-2` | Read Replica | 5434 |
| `proximity-redis` | Redis 캐시 | 6379 |
| `proximity-prometheus` | Metrics 수집 | 9090 |
| `proximity-grafana` | Dashboard 시각화 | 3000 |

---

## 주요 설계 결정

### 공간 검색: PostGIS `ST_DWithin` + GIST 인덱스

정확한 반경 검색(셀 경계 문제 없음), 단일 쿼리로 거리 계산·반경 필터·정렬 모두 처리. Geohash(셀 경계 문제), Quadtree(RDB 직렬화 비효율) 대비 선택.

### Read/Write 분리: CQRS (jOOQ + JPA)

읽기:쓰기 비율 **2,500:1** → 읽기 경로는 jOOQ type-safe DSL + Replica, 쓰기 경로는 JPA ORM + Primary. `AbstractRoutingDataSource`로 `@Transactional(readOnly = true)` 기반 자동 라우팅.

### 캐싱: Redis + Grid-based Cache Key

좌표를 0.01도 단위(~1.1km)로 반올림하여 deterministic 캐시 키 생성. 검색 TTL 10분, 상세 TTL 30분. 사업장 변경 시 3x3 Grid(9개 셀) 캐시 삭제.

### 아키텍처: Hexagonal (Port/Adapter)

Domain이 인프라에 의존하지 않으므로 단위 테스트가 간단하고, 기술 교체 시 Adapter만 수정.

### 프론트엔드: Vanilla JS + Leaflet/OpenStreetMap

프레임워크 없이 Vanilla HTML/CSS/JS + Leaflet. 카테고리 카드 기반 Yelp 스타일 UX + 무한 스크롤.

---

## Quickstart

### Prerequisites

- Docker & Docker Compose v2
- JDK 21+
- Gradle 8+
- (부하 테스트 시) k6 — `brew install k6`

### 서비스 실행

```bash
docker compose up -d
```

기동 순서: `postgres-primary (healthy) → replica-1, replica-2 (healthy) → redis (healthy) → backend → nginx`

### 서비스 접속

| 서비스 | URL | 비고 |
|--------|-----|------|
| Frontend | http://localhost | Nginx 경유 |
| API | http://localhost:8080/api | 직접 접근 |
| API (Nginx 경유) | http://localhost/api | Rate Limiting 적용 |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | |

### 종료

```bash
# 서비스 종료 (데이터 유지)
docker compose down

# 서비스 종료 + 모든 데이터 삭제 (완전 초기화)
docker compose down -v
```

---

## 대량 데이터 적재

### 기본 시드 (10K)

```bash
docker compose exec postgres-primary psql -U proximity -d proximity \
  -f /dev/stdin < backend/src/main/resources/seed/generate-seed-data.sql
```

### 원하는 규모로 적재

```bash
# 100K
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(100000);"

# 1M (인덱스 비활성화 → 적재 → 재생성 권장)
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "DROP INDEX IF EXISTS idx_business_location;"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ALTER TABLE businesses DISABLE TRIGGER trg_business_location;"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(1000000);"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "CREATE INDEX idx_business_location ON businesses USING GIST(location);"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ALTER TABLE businesses ENABLE TRIGGER trg_business_location;"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ANALYZE businesses;"
```

적재 후 Redis 캐시 초기화:

```bash
docker compose exec redis redis-cli FLUSHALL
```

### 적재 소요 시간 참고

| 규모 | 예상 소요 시간 |
|------|---------------|
| 10K | ~5초 |
| 100K | ~30초 |
| 500K | ~3분 |
| 1M | ~6분 |

---

## API 검증

### 소유주 회원가입 & 로그인

```bash
# 회원가입
curl -s -X POST http://localhost:8080/api/owners/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"테스트 사업주"}'

# 로그인 → JWT 토큰 발급
curl -s -X POST http://localhost:8080/api/owners/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```

### 검색

```bash
# 강남역 반경 1km 검색
curl -s "http://localhost:8080/api/search?latitude=37.4979&longitude=127.0276&radius=1"

# 카테고리 필터 검색
curl -s "http://localhost:8080/api/search?latitude=37.4979&longitude=127.0276&radius=1&category=카페"
```

### 사업장 CRUD

```bash
TOKEN="<JWT 토큰>"

# 등록
curl -s -X POST http://localhost:8080/api/businesses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"테스트 카페","address":"서울시 강남구 역삼동","latitude":37.4979,"longitude":127.0276,"phone":"02-1234-5678","category":"카페"}'

# 상세 조회
curl -s http://localhost:8080/api/businesses/1

# 수정
curl -s -X PUT http://localhost:8080/api/businesses/1/update \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"수정된 카페","address":"서울시 강남구 역삼동","latitude":37.4979,"longitude":127.0276,"phone":"02-1234-5678","category":"카페"}'

# 삭제
curl -s -X DELETE http://localhost:8080/api/businesses/1/delete \
  -H "Authorization: Bearer $TOKEN"
```

---

## 부하 테스트 (k6)

### 검색 부하 테스트

트래픽 패턴 (6분): `290 req/s (1분) → 500 req/s (2분) → 1000 req/s (2분) → 290 req/s (1분)`

```bash
k6 run k6/search-load-test.js
```

### 데이터 규모별 성능 비교

```bash
k6 run -e DATA_SCALE=10K k6/data-scale-test.js
k6 run -e DATA_SCALE=100K k6/data-scale-test.js
k6 run -e DATA_SCALE=1M k6/data-scale-test.js
```

### Success Criteria

| 지표 | 목표 |
|------|------|
| 검색 p95 | < 1000ms |
| 검색 p99 (피크) | < 2000ms |
| 에러율 | < 1% |
| 상세 조회 p95 | < 500ms |

---

## 모니터링

- **Grafana**: http://localhost:3000 (admin / admin) → Dashboards → `proximity-service`
- **Prometheus**: http://localhost:9090
- **Actuator**: http://localhost:8080/actuator/health

Custom Metrics:

| Metric | 설명 |
|--------|------|
| `proximity.search.query.duration` | ST_DWithin 쿼리 실행 시간 |
| `proximity.search.result.count` | 검색 결과 건수 |
| `proximity.cache.hit.ratio` | Grid 캐시 적중률 |
| `proximity.search.radius` | 요청 반경별 분포 |

---

## 트러블슈팅

### Replica가 기동되지 않을 때

```bash
docker compose down
docker volume rm weeks1_postgres-replica-1-data weeks1_postgres-replica-2-data
docker compose up -d
```

### Backend가 DB에 연결하지 못할 때

```bash
docker compose exec postgres-primary pg_isready -U proximity
docker compose logs -f backend
```

### Rate Limiting 429 발생

부하 테스트 시 Backend 직접 접근(port 8080)을 사용한다.

```bash
k6 run -e BASE_URL=http://localhost:8080 k6/search-load-test.js
```

---

## 프로젝트 구조

```
backend/
  src/main/java/com/proximity/
    adapter/in/web/          # Controller, Filter, ExceptionHandler
    adapter/out/persistence/ # jOOQ (Read), JPA (Write), Redis, JWT
    application/port/        # UseCase (in), Port (out)
    application/service/     # Service 구현체
    domain/                  # Business, Owner, BusinessHours, BusinessPhoto
    config/                  # DataSource, jOOQ, Redis, Security, Metrics
  src/main/resources/
    db/migration/            # Flyway 마이그레이션
    seed/                    # 시드 데이터 생성 SQL

frontend/
  index.html
  js/                        # app, map, geolocation, api, search, detail, admin, favorites, geohash-viz

docker/                      # Nginx, PostgreSQL Replica, Grafana, Prometheus 설정
k6/                          # 부하 테스트 스크립트
specs/                       # 설계 문서
```

---

## 상세 문서

- [시스템 설계 정리](specs/001-proximity-service/system-design-summary.md) — 설계 결정, 대안 비교, 규모 추정, 회고
- [Quickstart 가이드](specs/001-proximity-service/quickstart.md) — 실행, 데이터 적재, 부하 테스트 상세
- [API 명세](specs/001-proximity-service/contracts/api.yaml) — OpenAPI Spec
