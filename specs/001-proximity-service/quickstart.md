# Quickstart: 주변 사업장 검색 서비스 (Proximity Service)

## Prerequisites

- Docker & Docker Compose v2
- JDK 21+
- Gradle 8+
- (부하 테스트 시) k6 — `brew install k6`

---

## 1. 서비스 실행

### 1-A. 전체 스택 한 번에 실행

```bash
docker compose up -d
```

이 명령 하나로 아래 8개 컨테이너가 모두 기동된다.

| 컨테이너 | 역할 | 포트 |
|-----------|------|------|
| `proximity-postgres-primary` | PostgreSQL Primary (Write) | 5432 |
| `proximity-postgres-replica-1` | PostgreSQL Replica (Read) | 5433 |
| `proximity-postgres-replica-2` | PostgreSQL Replica (Read) | 5434 |
| `proximity-redis` | Redis 캐시 | 6379 |
| `proximity-backend` | Spring Boot API 서버 | 8080 |
| `proximity-nginx` | Nginx (정적 파일 + API 프록시) | 80 |
| `proximity-prometheus` | Metrics 수집 | 9090 |
| `proximity-grafana` | Dashboard 시각화 | 3000 |

기동 순서는 `depends_on` + `healthcheck`로 자동 제어된다:
```
postgres-primary (healthy) → replica-1, replica-2 (healthy) → redis (healthy) → backend → nginx
                                                                                       → prometheus → grafana
```

### 1-B. 단계별 실행 (개발 모드)

Backend를 로컬에서 직접 실행하고 싶을 때:

```bash
# 1) 인프라만 기동
docker compose up -d postgres-primary postgres-replica-1 postgres-replica-2 redis

# 2) Primary healthcheck 통과 대기
docker compose exec postgres-primary pg_isready -U proximity

# 3) Backend 로컬 실행 (Flyway 마이그레이션 자동 실행됨)
cd backend && ./gradlew bootRun

# 4) Frontend는 Nginx로 서빙하거나 직접 브라우저에서 열기
docker compose up -d nginx
# 또는 frontend/index.html을 브라우저로 직접 열기 (API는 localhost:8080으로 호출)
```

### 1-C. 헬스 체크

```bash
# 전체 컨테이너 상태 확인
docker compose ps

# Backend 헬스 체크
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# PostgreSQL Replication 상태 확인
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT client_addr, state, sent_lsn, replay_lsn FROM pg_stat_replication;"

# Redis 연결 확인
docker compose exec redis redis-cli ping
```

### 1-D. 서비스 접속

| 서비스 | URL | 비고 |
|--------|-----|------|
| Frontend | http://localhost | Nginx 경유 |
| API | http://localhost:8080/api | 직접 접근 |
| API (Nginx 경유) | http://localhost/api | Rate Limiting 적용 |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | |

### 1-E. 종료 및 초기화

```bash
# 서비스 종료 (데이터 유지)
docker compose down

# 서비스 종료 + 모든 데이터 삭제 (완전 초기화)
docker compose down -v
```

---

## 2. 대량 데이터 적재

DB에 시드 데이터를 생성하는 SQL 함수가 `backend/src/main/resources/seed/generate-seed-data.sql`에 준비되어 있다. `generate_proximity_seed(N)` 함수를 호출하면 N개의 사업장이 생성된다.

### 데이터 분포

| 지역 | 비율 | 좌표 범위 |
|------|------|-----------|
| 강남역 핫스팟 | 10% | 37.4879~37.5079, 126.0176~127.0376 |
| 서울 전체 | 50% | 37.413~37.715, 126.734~127.183 |
| 부산 | 20% | 35.053~35.238, 128.852~129.219 |
| 대전 | 15% | 36.282~36.430, 127.299~127.480 |
| 대구 | 15% | 35.797~35.922, 128.480~128.696 |

카테고리: 카페, 식당, 편의점, 약국, 병원, 미용실, 세탁소, 문구점, 서점, 꽃집 (균등 분포)

### 2-A. 기본 시드 (10K)

```bash
docker compose exec postgres-primary psql -U proximity -d proximity \
  -f /dev/stdin < backend/src/main/resources/seed/generate-seed-data.sql
```

SQL 파일 마지막에 `SELECT generate_proximity_seed(10000);`이 포함되어 있어 기본 10K 건이 생성된다.

### 2-B. 원하는 규모로 적재

SQL 파일의 함수 정의만 로드한 뒤, 원하는 수량을 직접 호출한다.

```bash
# 1) 함수 정의 + Owner 생성 (기본 10K 포함)
docker compose exec postgres-primary psql -U proximity -d proximity \
  -f /dev/stdin < backend/src/main/resources/seed/generate-seed-data.sql

# 2) 추가 데이터 적재 (원하는 수량)
# 100K
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(100000);"

# 500K
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(500000);"

# 1M
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(1000000);"
```

> 함수가 이미 drop된 후라면 SQL 파일의 마지막 3줄(`SELECT`, `DROP FUNCTION`, `DROP TABLE`)을 제거하고 다시 로드해야 한다.

### 2-C. 대량 적재 시 권장 절차

100K 이상 적재 시 다음 순서를 따른다.

```bash
# 1) 기존 시드 데이터 정리 (필요 시)
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "DELETE FROM business_photos; DELETE FROM business_hours; DELETE FROM businesses WHERE name LIKE '%시드%' OR name ~ '^(카페|식당|편의점|약국|병원|미용실|세탁소|문구점|서점|꽃집) [0-9]+';"

# 2) 인덱스 비활성화 (대량 INSERT 속도 향상)
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "DROP INDEX IF EXISTS idx_business_location;"

# 3) Trigger 비활성화 (location 컬럼을 SQL에서 직접 설정하므로)
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ALTER TABLE businesses DISABLE TRIGGER trg_business_location;"

# 4) 데이터 적재
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(1000000);"

# 5) 인덱스 재생성
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "CREATE INDEX idx_business_location ON businesses USING GIST(location);"

# 6) Trigger 재활성화
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ALTER TABLE businesses ENABLE TRIGGER trg_business_location;"

# 7) ANALYZE (통계 갱신)
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ANALYZE businesses;"
```

### 2-D. 적재 결과 확인

```bash
# 총 건수
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT count(*) FROM businesses;"

# 도시별 분포 확인
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT
        CASE
          WHEN latitude BETWEEN 37.413 AND 37.715 THEN '서울'
          WHEN latitude BETWEEN 35.053 AND 35.238 THEN '부산'
          WHEN latitude BETWEEN 36.282 AND 36.430 THEN '대전'
          WHEN latitude BETWEEN 35.797 AND 35.922 THEN '대구'
          ELSE '기타'
        END AS city,
        count(*),
        round(count(*)::numeric / (SELECT count(*) FROM businesses) * 100, 1) AS pct
      FROM businesses
      GROUP BY 1
      ORDER BY 2 DESC;"

# 카테고리별 분포
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT category, count(*) FROM businesses GROUP BY category ORDER BY 2 DESC;"

# GIST 인덱스 사용 여부 확인
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "EXPLAIN ANALYZE
      SELECT id, name, ST_Distance(location, ST_SetSRID(ST_MakePoint(127.0276, 37.4979), 4326)::geography) AS distance
      FROM businesses
      WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(127.0276, 37.4979), 4326)::geography, 1000)
      ORDER BY distance
      LIMIT 20;"
```

### 2-E. 적재 소요 시간 참고

| 규모 | 예상 소요 시간 | 비고 |
|------|---------------|------|
| 10K | ~5초 | SQL 파일 기본 실행 |
| 100K | ~30초 | |
| 500K | ~3분 | 인덱스 비활성화 권장 |
| 1M | ~6분 | 인덱스 비활성화 필수 |

> 시간은 Docker Desktop 환경 기준 추정치이며, 호스트 성능에 따라 달라질 수 있다.

---

## 3. Redis 캐시 초기화

대량 데이터 적재 후 기존 캐시를 비워야 정확한 검색 결과가 반환된다.

```bash
# 전체 캐시 삭제
docker compose exec redis redis-cli FLUSHALL

# 캐시 키 목록 확인
docker compose exec redis redis-cli KEYS "*"

# 캐시 적중률 모니터링
docker compose exec redis redis-cli INFO stats | grep keyspace
```

---

## 4. 부하 테스트 (k6)

### 4-A. 사전 준비

```bash
# k6 설치 (macOS)
brew install k6

# k6 스크립트 검증
bash k6/validate.sh
```

### 4-B. 검색 부하 테스트

`k6/search-load-test.js` — Spec의 피크 QPS를 시뮬레이션한다.

**테스트 시나리오:**
- 80% 검색 (`GET /api/search`) — 30% 강남 핫스팟, 70% 전국 랜덤
- 15% 상세 조회 (`GET /api/businesses/{id}`)
- 5% 쓰기 (`POST /api/businesses`)

**트래픽 패턴 (6분):**
```
290 req/s (1분) → 500 req/s (2분) → 1000 req/s (2분) → 290 req/s (1분)
```

**Success Criteria:**
- `search_latency` p95 < 1000ms
- `search_latency` p99 < 2000ms
- `search_errors` rate < 1%

```bash
# 기본 실행 (Backend 직접)
k6 run k6/search-load-test.js

# Nginx 경유 (Rate Limiting 포함)
k6 run -e BASE_URL=http://localhost k6/search-load-test.js

# 인증이 필요한 쓰기 테스트를 포함하려면 토큰 전달
# 1) 토큰 발급
TOKEN=$(curl -s -X POST http://localhost:8080/api/owners/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seed@proximity.dev","password":"<password>"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")

# 2) 토큰과 함께 실행
k6 run -e AUTH_TOKEN=$TOKEN k6/search-load-test.js
```

### 4-C. 데이터 규모별 성능 비교 테스트

`k6/data-scale-test.js` — 강남역 주변에서 5가지 반경(0.5/1/2/5/20km)을 순회하며 쿼리 성능을 측정한다.

**테스트 패턴:** 50 req/s, 2분간 constant-arrival-rate

```bash
# 10K 데이터 기준
k6 run -e DATA_SCALE=10K k6/data-scale-test.js

# 100K 데이터 적재 후
k6 run -e DATA_SCALE=100K k6/data-scale-test.js

# 1M 데이터 적재 후
k6 run -e DATA_SCALE=1M k6/data-scale-test.js
```

### 4-D. 규모별 비교 테스트 전체 흐름

```bash
# === 10K ===
docker compose down -v && docker compose up -d
sleep 30  # 기동 대기
docker compose exec postgres-primary psql -U proximity -d proximity \
  -f /dev/stdin < backend/src/main/resources/seed/generate-seed-data.sql
docker compose exec redis redis-cli FLUSHALL
k6 run -e DATA_SCALE=10K k6/data-scale-test.js 2>&1 | tee k6/results-10K.txt

# === 100K ===
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(90000);"
# ↑ 기존 10K + 90K = 100K
docker compose exec postgres-primary psql -U proximity -d proximity -c "ANALYZE businesses;"
docker compose exec redis redis-cli FLUSHALL
k6 run -e DATA_SCALE=100K k6/data-scale-test.js 2>&1 | tee k6/results-100K.txt

# === 1M ===
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "DROP INDEX IF EXISTS idx_business_location;"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ALTER TABLE businesses DISABLE TRIGGER trg_business_location;"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT generate_proximity_seed(900000);"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "CREATE INDEX idx_business_location ON businesses USING GIST(location);"
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "ALTER TABLE businesses ENABLE TRIGGER trg_business_location;"
docker compose exec postgres-primary psql -U proximity -d proximity -c "ANALYZE businesses;"
docker compose exec redis redis-cli FLUSHALL
k6 run -e DATA_SCALE=1M k6/data-scale-test.js 2>&1 | tee k6/results-1M.txt
```

### 4-E. 결과 해석

k6 실행 완료 후 출력되는 summary에서 확인할 항목:

```
query_latency............: avg=XXms  min=XXms  med=XXms  max=XXms  p(90)=XXms  p(95)=XXms
```

| 지표 | 목표 (Spec) | 확인 방법 |
|------|-------------|-----------|
| 검색 p95 | < 1000ms | `search_latency` p(95) |
| 검색 p99 (피크) | < 2000ms | `search_latency` p(99) |
| 에러율 | < 1% | `search_errors` rate |
| 상세 조회 p95 | < 500ms | `detail_latency` p(95) |

---

## 5. API 검증 시나리오

### 5-A. 소유주 회원가입 & 로그인

```bash
# 회원가입
curl -s -X POST http://localhost:8080/api/owners/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"테스트 사업주"}' | python3 -m json.tool

# 로그인 → JWT 토큰 발급
curl -s -X POST http://localhost:8080/api/owners/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' | python3 -m json.tool
```

### 5-B. 사업장 CRUD

```bash
TOKEN="<위에서 발급받은 JWT 토큰>"

# 등록
curl -s -X POST http://localhost:8080/api/businesses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"테스트 카페","address":"서울시 강남구 역삼동","latitude":37.4979,"longitude":127.0276,"phone":"02-1234-5678","category":"카페"}' | python3 -m json.tool

# 상세 조회
curl -s http://localhost:8080/api/businesses/1 | python3 -m json.tool

# 수정
curl -s -X PUT http://localhost:8080/api/businesses/1/update \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"수정된 카페","address":"서울시 강남구 역삼동","latitude":37.4979,"longitude":127.0276,"phone":"02-1234-5678","category":"카페"}' | python3 -m json.tool

# 삭제
curl -s -X DELETE http://localhost:8080/api/businesses/1/delete \
  -H "Authorization: Bearer $TOKEN"
```

### 5-C. 검색

```bash
# 강남역 반경 1km 검색
curl -s "http://localhost:8080/api/search?latitude=37.4979&longitude=127.0276&radius=1" | python3 -m json.tool

# 서울 시청 반경 5km 검색
curl -s "http://localhost:8080/api/search?latitude=37.5665&longitude=126.9780&radius=5" | python3 -m json.tool

# 빈 결과 (EC-1: 바다 한가운데)
curl -s "http://localhost:8080/api/search?latitude=33.0&longitude=124.0&radius=0.5" | python3 -m json.tool

# 유효하지 않은 좌표 (EC-2: 400 Bad Request)
curl -s "http://localhost:8080/api/search?latitude=999&longitude=127.0&radius=1" | python3 -m json.tool

# 유효하지 않은 반경 (400 Bad Request)
curl -s "http://localhost:8080/api/search?latitude=37.5&longitude=127.0&radius=3" | python3 -m json.tool
```

---

## 6. 모니터링

### Grafana Dashboard

1. http://localhost:3000 접속 (admin / admin)
2. 좌측 메뉴 → Dashboards → `proximity-service` 선택
3. 확인 가능한 메트릭:
   - 검색 쿼리 Latency (히스토그램)
   - 검색 결과 건수 분포
   - 캐시 적중률
   - 반경별 검색 빈도

### Prometheus 직접 쿼리

```
http://localhost:9090/graph
```

유용한 PromQL 쿼리:

```promql
# 검색 API 평균 응답 시간
rate(http_server_requests_seconds_sum{uri="/api/search"}[5m])
/ rate(http_server_requests_seconds_count{uri="/api/search"}[5m])

# 검색 API p95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/search"}[5m]))

# 분당 요청 수
rate(http_server_requests_seconds_count{uri="/api/search"}[1m]) * 60
```

### Spring Boot Actuator

```bash
# 전체 메트릭 목록
curl -s http://localhost:8080/actuator/metrics | python3 -m json.tool

# 특정 메트릭 상세
curl -s http://localhost:8080/actuator/metrics/http.server.requests | python3 -m json.tool

# Prometheus 형식 (Grafana가 수집하는 데이터)
curl -s http://localhost:8080/actuator/prometheus | head -50
```

---

## 7. 트러블슈팅

### Replica가 기동되지 않을 때

```bash
# Replica 볼륨 삭제 후 재기동
docker compose down
docker volume rm weeks1_postgres-replica-1-data weeks1_postgres-replica-2-data
docker compose up -d
```

### Backend가 DB에 연결하지 못할 때

```bash
# Primary healthcheck 확인
docker compose exec postgres-primary pg_isready -U proximity

# Backend 로그 확인
docker compose logs -f backend

# PostGIS Extension 확인
docker compose exec postgres-primary psql -U proximity -d proximity \
  -c "SELECT PostGIS_Version();"
```

### 캐시 관련 이슈

```bash
# Redis 캐시 전체 삭제
docker compose exec redis redis-cli FLUSHALL

# 특정 검색 캐시만 삭제
docker compose exec redis redis-cli KEYS "search:*" | xargs -I {} docker compose exec redis redis-cli DEL {}
```

### Rate Limiting 테스트 시 429 발생

Nginx Rate Limiting(60req/min)이 적용되어 있다. 부하 테스트 시에는 Backend 직접 접근(port 8080)을 사용한다.

```bash
# Nginx 경유 (Rate Limiting 적용)
k6 run -e BASE_URL=http://localhost k6/search-load-test.js

# Backend 직접 (Nginx 60req/min 우회, Backend 300req/min 적용)
k6 run -e BASE_URL=http://localhost:8080 k6/search-load-test.js
```
