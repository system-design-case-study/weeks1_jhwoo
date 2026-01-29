# Phase 09: Observability — Prometheus + Grafana + Custom Metrics

**Status**: pending
**Prerequisites**: Phase 05 완료
**Covers**: research.md (Decision 5), plan.md (컨테이너 구성), spec.md (SC-001, SC-006)

## 목표
Micrometer Custom Metrics를 구현하고, Prometheus 수집 + Grafana Dashboard를 구성하여 ST_DWithin 쿼리 성능과 캐시 효율을 실시간 모니터링한다.

## Tasks

### 9.1 Micrometer Custom Metrics 구현
- **`proximity.search.query.duration`** (Histogram)
  - JooqSearchAdapter에서 ST_DWithin 쿼리 실행 시간 측정
  - `Timer.builder("proximity.search.query.duration").register(meterRegistry)`
- **`proximity.search.result.count`** (Summary)
  - 검색 결과 건수 기록
  - `DistributionSummary.builder("proximity.search.result.count").register(meterRegistry)`
- **`proximity.cache.hit.ratio`** (Gauge)
  - 캐시 적중 / 전체 요청 비율
  - `Gauge.builder("proximity.cache.hit.ratio", ...).register(meterRegistry)`
- **`proximity.search.radius`** (Histogram)
  - 요청 반경별 분포 (0.5/1/2/5/20km)

### 9.2 Prometheus 설정
- `prometheus.yml` 작성
  - Spring Boot Actuator endpoint scrape 설정 (`/actuator/prometheus`)
  - scrape_interval: 15s
- Docker Compose prometheus 서비스 설정

### 9.3 Grafana Dashboard 구성
- Dashboard JSON provisioning 파일 작성
- 패널 4개:
  1. **ST_DWithin 쿼리 Latency**: p50/p95/p99 시계열 그래프
  2. **데이터 규모별 쿼리 성능 변화**: Latency 추이 (수동 데이터 삽입으로 확인)
  3. **Cache Hit Ratio 실시간 추이**: 적중률 시계열
  4. **GIST 인덱스 크기**: `pg_relation_size` 주기적 수집 (PostgreSQL Exporter 또는 커스텀)
- Grafana data source: Prometheus 자동 프로비저닝

### 9.4 K6 부하 테스트 스크립트
- `k6/search-load-test.js`:
  - 기본 시나리오: QPS 290 → 500 → 1,000 단계별 증가
  - 핫스팟 시뮬레이션: 서울 강남 좌표 집중 (37.4979, 127.0276)
  - 혼합 시나리오: 검색 80% + 상세 조회 15% + 쓰기 5%
- `k6/data-scale-test.js`:
  - 데이터 규모별 성능 프로파일링 (10K, 100K, 500K, 1M rows)
  - 각 규모에서 ST_DWithin 쿼리 Latency 측정

### 9.5 샘플 데이터 생성기
- `backend/src/main/resources/seed/` 또는 별도 스크립트
  - 한국 좌표 범위 내 무작위 사업장 데이터 생성
  - 10K / 100K / 500K / 1M 규모별 시드 데이터
  - 서울 강남 좌표 근처 핫스팟 집중 데이터 포함

## TDD 접근

### 테스트 우선 작성
- **T-9.1**: Custom Metrics 등록 테스트
  - `proximity.search.query.duration` 메트릭이 Actuator `/prometheus` 엔드포인트에 노출되는지 확인
  - 검색 API 호출 후 `proximity.search.result.count` 값 증가 확인
- **T-9.2**: 캐시 히트율 Gauge 테스트
  - Cache Hit 발생 시 ratio 증가 확인
  - Cache Miss 발생 시 ratio 감소 확인
- **T-9.3**: K6 스크립트 문법 검증
  - `k6 validate` 명령으로 스크립트 유효성 확인
- **T-9.4**: GIST 인덱스 Scan 검증 테스트 (Testcontainers)
  - 10K 데이터 삽입 → `EXPLAIN ANALYZE` 실행 → Index Scan 사용 확인
  - 반경 20km로 전체 데이터 조회 → Seq Scan 전환 여부 확인

## 완료 기준
- [ ] Micrometer Custom Metrics 4종 Actuator 노출 확인
- [ ] Prometheus scrape 설정 → 메트릭 수집 확인
- [ ] Grafana Dashboard 4개 패널 표시 확인
- [ ] K6 부하 테스트 스크립트 실행 가능
- [ ] 샘플 데이터 생성기 동작 확인
- [ ] GIST Index Scan 확인 테스트 통과

## 관련 Spec 항목
- research.md Decision 5 (K6 + Prometheus + Grafana) 전체
  - Custom Metrics, Grafana Dashboard 패널, 테스트 시나리오, 데이터 규모별 프로파일링
- spec.md SC-001 (검색 p95 <1초), SC-002 (동시 1,000명), SC-006 (피크 p99 <2초)
- plan.md 컨테이너 구성 (prometheus, grafana)
