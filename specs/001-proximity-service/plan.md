# Implementation Plan: 주변 사업장 검색 서비스 (Proximity Service)

**Branch**: `001-proximity-service` | **Date**: 2026-01-30 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-proximity-service/spec.md`

## Summary

사용자 위치(위도/경도) 기반 반경 내 사업장 검색, 사업장 CRUD, 상세 정보 조회 서비스를 구현한다. PostGIS `ST_DWithin`으로 정확한 반경 검색을 수행하고, Grid-based 캐시 키로 Redis 캐싱 효율을 극대화하며, Hexagonal Architecture로 도메인과 인프라를 분리한다.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.x, Spring Data JPA, Hibernate Spatial (`org.hibernate.orm:hibernate-spatial`), jOOQ (`org.jooq:jooq`, `org.jooq:jooq-codegen`), Flyway, Spring Cache (Redis)
**Storage**: PostgreSQL 16 + PostGIS 3.4 (Primary 1 + Replica 2) + Redis 7
**Testing**: JUnit 5, Testcontainers (PostgreSQL + PostGIS)
**Target Platform**: Docker Compose (Linux containers)
**Project Type**: Web application (backend + frontend)
**Performance Goals**: 검색 p95 <1초, 상세 조회 p95 <0.5초, 피크 p99 <2초
**Constraints**: Read:Write 2500:1, 피크 QPS ~1,450 (검색+상세), 24시간 Eventual Consistency
**Scale/Scope**: DAU 1M, 등록 사업장 1M

## Constitution Check

| 원칙 | 판정 | 근거 |
| ---- | ---- | ---- |
| **I. Performance First** | PASS | PostGIS GIST 인덱스 15~60ms 쿼리, Redis 캐시 1~5ms, Read/Write DB 분리 |
| **II. Privacy by Design** | PASS | 사용자 위치를 Grid 반올림(0.01도)하여 정밀도 제한, 원본 좌표 미저장, 캐시 키에 반올림 좌표만 사용 |
| **III. Scalability** | PASS | Stateless 서비스, Replica 수평 확장, Redis 캐시 분산 가능 |
| **IV. Eventual Consistency** | PASS | 비동기 Streaming Replication, TTL 기반 캐시 무효화, 24시간 윈도우 내 반영 |
| **V. Explainability** | PASS | 모든 기술 결정에 2개 이상 대안 비교, research.md에 Trade-off 문서화 |

## Project Structure

### Documentation (this feature)

```text
specs/001-proximity-service/
├── plan.md              # This file
├── research.md          # 기술 결정 문서
├── data-model.md        # 데이터 모델 정의
├── quickstart.md        # 로컬 실행 가이드
├── contracts/           # API 계약
│   └── api.yaml
└── tasks.md             # 구현 태스크 (별도 생성)
```

### Source Code (repository root)

```text
backend/src/main/java/com/proximity/
├── application/                    # UseCase (Inbound Port 구현체)
│   ├── port/in/                    # Inbound Port interfaces
│   │   ├── SearchUseCase.java
│   │   └── BusinessUseCase.java
│   ├── port/out/                   # Outbound Port interfaces
│   │   ├── SearchPort.java         # 읽기 전용 Port (jOOQ Adapter)
│   │   ├── BusinessReadPort.java   # 상세 조회 읽기 Port (jOOQ Adapter)
│   │   ├── BusinessWritePort.java  # 쓰기 전용 Port (JPA Adapter)
│   │   ├── CachePort.java
│   │   └── AuthPort.java
│   ├── service/                    # UseCase 구현
│   │   ├── SearchService.java
│   │   └── BusinessService.java
│   └── dto/                        # Application DTO
│       ├── SearchRequest.java
│       ├── SearchResponse.java
│       ├── BusinessDetailResponse.java
│       └── BusinessCreateRequest.java
├── adapter/
│   ├── in/web/                     # REST Controllers (Inbound Adapter)
│   │   ├── SearchController.java
│   │   └── BusinessController.java
│   └── out/
│       ├── persistence/
│       │   ├── read/               # jOOQ Adapters (Read — Replica DataSource)
│       │   │   ├── JooqSearchAdapter.java
│       │   │   └── JooqBusinessReadAdapter.java
│       │   ├── write/              # JPA Adapters (Write — Primary DataSource)
│       │   │   ├── JpaBusinessWriteAdapter.java
│       │   │   └── entity/
│       │   │       ├── BusinessJpaEntity.java
│       │   │       ├── BusinessHoursJpaEntity.java
│       │   │       └── BusinessPhotoJpaEntity.java
│       │   └── jooq/              # jOOQ Generated Code (codegen 자동 생성)
│       ├── cache/                  # Redis Adapter
│       │   └── RedisCacheAdapter.java
│       └── auth/                   # JWT Adapter
│           └── JwtAuthAdapter.java
├── domain/                         # Domain Entities (순수 도메인)
│   ├── Business.java
│   ├── BusinessHours.java
│   ├── BusinessPhoto.java
│   ├── Geohash.java                # Manual Geohash 구현 (학습용, 라이브러리 미사용)
│   └── Owner.java
└── config/                         # Spring Configuration
    ├── DataSourceConfig.java       # Read/Write 분리 (AbstractRoutingDataSource)
    ├── JooqConfig.java             # jOOQ DSLContext + Replica DataSource 설정
    ├── RedisConfig.java
    └── SecurityConfig.java

backend/src/main/resources/
├── application.yml
└── db/migration/                   # Flyway 마이그레이션
    └── V1__init.sql

backend/src/test/java/com/proximity/
├── application/service/            # UseCase 단위 테스트
├── adapter/in/web/                 # Controller 통합 테스트
└── adapter/out/persistence/        # Repository 통합 테스트 (Testcontainers)

frontend/
├── index.html
├── style.css
├── js/
│   ├── app.js              # 진입점, 모드 전환 (검색/관리)
│   ├── map.js              # Leaflet 지도 초기화, LayerGroup 관리
│   ├── geolocation.js      # Geolocation API, fallback 처리 (EC-6, EC-7)
│   ├── api.js              # Backend API fetch 래퍼
│   ├── search.js           # 검색 UI, Circle/마커/리스트 렌더링
│   ├── detail.js           # 상세 패널 렌더링 (Popup + 사이드 패널)
│   ├── admin.js            # 관리자 모드, 지도 클릭 좌표 입력, CRUD
│   └── geohash-viz.js      # Geohash 셀 Rectangle 시각화
└── assets/
    └── no-photo.svg
```

**Structure Decision**: Hexagonal Architecture를 채택하여 `application` (Port/UseCase), `adapter` (In/Out), `domain` (순수 엔티티)으로 분리. 도메인 로직이 인프라에 의존하지 않아 테스트 용이성과 교체 유연성을 확보한다.

## Architecture Diagram

```
[Client (Browser)]
  ├── Leaflet Map (OSM 타일, 마커, Circle, Popup, Rectangle)
  ├── Side Panel (검색, 리스트, 상세, 관리)
  └── Browser Geolocation API (EC-6/EC-7 fallback)
       │
       ▼
[Nginx - Load Balancer]
       │
       ├── /api/search ──▶ [SearchController]
       │                        │
       │                        ▼
       │                   [SearchUseCase]
       │                    │         │
       │              CachePort    SearchPort (jOOQ)
       │                │              │
       │           [Redis]    [JooqSearchAdapter]
       │                              │
       │                    [PostgreSQL+PostGIS]
       │                         (Replica)
       │
       ├── /api/businesses/{id} ──▶ [BusinessController]
       │                                │
       │                                ▼
       │                          [BusinessUseCase]
       │                           │         │
       │                     CachePort    BusinessReadPort (jOOQ)
       │                       │              │
       │                  [Redis]    [JooqBusinessReadAdapter]
       │                                     │
       │                           [PostgreSQL+PostGIS]
       │                                (Replica)
       │
       └── /api/businesses (CUD) ──▶ [BusinessController]
                                          │
                                          ▼
                                    [BusinessUseCase]
                                     │    │    │
                                AuthPort  │  CachePort
                                  │       │      │
                               [JWT]  BusinessWritePort (JPA)  [Redis]
                                        │              (Invalidate)
                                  [JpaBusinessWriteAdapter]
                                          │
                                  [PostgreSQL+PostGIS]
                                      (Primary)
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Hexagonal Architecture (Port/Adapter) | 도메인과 인프라 분리로 PostGIS Adapter 교체 용이, UseCase 단위 테스트 가능 | Layered Architecture는 Repository 구현체에 도메인이 직접 의존하여 DB 교체 시 도메인 수정 필요 |
| Read/Write DB 분리 (AbstractRoutingDataSource) | Read:Write 2500:1 비율에서 Replica 활용 극대화 | 단일 DataSource는 피크 시 Primary에 읽기 부하 집중 |
| CQRS: jOOQ (Read) + JPA (Write) | 공간 검색 쿼리의 type-safe DSL 작성, 컴파일 타임 SQL 검증 | JPA native query는 문자열 기반으로 PostGIS 함수 호출이 장황하고 타입 안전성 없음 |
| Manual Geohash 구현 (학습용) | 2D→1D 매핑 원리(Z-order curve, bit interleaving) 체득, PostGIS 선택 근거 실험적 검증 | 운영 용도가 아닌 학습 목적 — PostGIS 결과와 비교하여 셀 경계 문제를 직접 확인 |
| Split-view 레이아웃 (지도 + 사이드 패널) | 지도 마커와 리스트를 동시에 보여주어 위치 기반 UX의 핵심 가치 전달 | 지도만 또는 리스트만으로는 검색 결과의 공간적 분포를 파악할 수 없음 |
| JS 모듈 분리 (8파일) | 단일 파일 500줄+ 방지, 관심사별 분리 (지도/검색/관리/API/시각화) | 단일 app.js는 Geolocation + Leaflet + CRUD + Geohash 시각화가 뒤섞여 유지보수 불가 |
