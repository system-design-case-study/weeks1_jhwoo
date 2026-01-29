# Proximity Service — 구현 진행 현황

**Feature Branch**: `001-proximity-service`
**Last Updated**: 2026-01-30

## Phase 진행 상태

| Phase | 제목 | Status | Task 파일 | Spec 커버리지 |
|-------|------|--------|-----------|--------------|
| 01 | 프로젝트 초기화 + 인프라 설정 | ⬜ pending | [phase-01](specs/001-proximity-service/tasks/phase-01-project-init.md) | Decision 2, 4, 7 / data-model DDL / EC-3 UNIQUE |
| 02 | Domain 엔티티 + Port 인터페이스 | ⬜ pending | [phase-02](specs/001-proximity-service/tasks/phase-02-domain-ports.md) | Key Entities / FR-001~010 / Decision 7 CQRS Port |
| 03 | 쓰기 경로 (JPA Write) | ⬜ pending | [phase-03](specs/001-proximity-service/tasks/phase-03-write-path.md) | US3 / FR-006~009 / EC-2, EC-3, EC-5 |
| 04 | 읽기 경로 (jOOQ Read) | ⬜ pending | [phase-04](specs/001-proximity-service/tasks/phase-04-read-path.md) | US1, US2 / FR-001~005, FR-010 / EC-1, EC-2, EC-5 / Decision 1, 7 |
| 05 | 캐시 계층 (Redis) | ⬜ pending | [phase-05](specs/001-proximity-service/tasks/phase-05-cache.md) | Decision 3 / SC-001, SC-004 / Privacy |
| 06 | 인증/인가 (JWT) | ⬜ pending | [phase-06](specs/001-proximity-service/tasks/phase-06-auth.md) | FR-006, FR-009 / US3 Scenario 4 |
| 07 | Edge Cases + Error Handling | ⬜ pending | [phase-07](specs/001-proximity-service/tasks/phase-07-error-handling.md) | EC-1~EC-5 전체 / ErrorResponse |
| 08 | Geohash 학습 구현 | ⬜ pending | [phase-08](specs/001-proximity-service/tasks/phase-08-geohash.md) | Decision 1 Geohash / api.yaml /search/geohash |
| 09 | Observability (Prometheus/Grafana) | ⬜ pending | [phase-09](specs/001-proximity-service/tasks/phase-09-observability.md) | Decision 5 전체 / SC-001, SC-002, SC-006 |
| 10 | Frontend (Leaflet 지도) + E2E 통합 테스트 | ⬜ pending | [phase-10](specs/001-proximity-service/tasks/phase-10-frontend-e2e.md) | US1~3 전체 / EC-1~7 전체 / SC-001~006 전체 / Decision 6 |

## Spec 항목별 커버리지 매핑

### Functional Requirements

| FR | 설명 | Phase |
|----|------|-------|
| FR-001 | 위도/경도/반경 기반 사업장 검색 | 04, 10 |
| FR-002 | 반경 선택지 (0.5/1/2/5/20km) | 04, 07, 10 |
| FR-003 | 검색 결과에 이름/주소/거리 포함 | 04, 10 |
| FR-004 | 명시적 검색만, 자동 갱신 없음 | 10 |
| FR-005 | 사업장 상세 정보 조회 | 04, 10 |
| FR-006 | 인증된 소유주 CRUD | 03, 06, 10 |
| FR-007 | 24시간 이내 검색 반영 | 03, 05, 10 |
| FR-008 | 좌표 유효성 검증 | 02, 03, 07 |
| FR-009 | 소유주 아닌 사용자 CRUD 거부 | 03, 06, 07, 10 |
| FR-010 | 거리 순 정렬 | 04, 10 |

### Edge Cases

| EC | 설명 | Phase |
|----|------|-------|
| EC-1 | 빈 결과 → 200 + 안내 | 04, 07, 10 |
| EC-2 | 유효하지 않은 좌표 → 400 | 02, 03, 04, 07, 10 |
| EC-3 | 중복 등록 → 409 | 01, 03, 07, 10 |
| EC-4 | 트래픽 초과 → 429 | 07, 10 |
| EC-5 | 삭제 후 조회 → 404 | 03, 04, 07, 10 |
| EC-6 | Geolocation API 실패 → fallback | 10 |
| EC-7 | HTTPS 미사용 → fallback | 10 |

### Success Criteria

| SC | 설명 | Phase |
|----|------|-------|
| SC-001 | 검색 p95 <1초 | 05, 09, 10 |
| SC-002 | 동시 1,000명 처리 | 09, 10 |
| SC-003 | 24시간 이내 99% 반영 | 05, 10 |
| SC-004 | 상세 조회 p95 <0.5초 | 05, 09, 10 |
| SC-005 | 거리 정확도 ±5% | 10 |
| SC-006 | 피크 p99 <2초 | 09, 10 |

### Research Decisions

| Decision | 설명 | Phase |
|----------|------|-------|
| D1 | PostGIS ST_DWithin + Grid Cache + Manual Geohash | 04, 05, 08 |
| D2 | PostgreSQL + PostGIS + Streaming Replication | 01 |
| D3 | Redis + Grid-based Cache Key | 05 |
| D4 | Hexagonal Architecture | 01, 02 |
| D5 | K6 + Prometheus + Grafana | 09 |
| D6 | Vanilla HTML/JS SPA | 10 |
| D7 | CQRS: jOOQ (Read) + JPA (Write) | 01, 02, 03, 04 |

## Phase 의존 관계

```
Phase 01 (인프라)
    │
    ▼
Phase 02 (Domain + Port)
    │
    ├──────────────────┐
    ▼                  ▼
Phase 03 (Write)   Phase 06 (Auth)
    │                  │
    ▼                  │
Phase 04 (Read) ◀──────┘
    │
    ├──────────────────┐
    ▼                  ▼
Phase 05 (Cache)   Phase 08 (Geohash)
    │
    ▼
Phase 09 (Observability)
    │
    ▼
Phase 07 (Error Handling) ◀── Phase 04, 05, 06
    │
    ▼
Phase 10 (Frontend + E2E)
```
