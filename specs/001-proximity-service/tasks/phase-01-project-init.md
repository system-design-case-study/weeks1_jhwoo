# Phase 01: 프로젝트 초기화 + 인프라 설정

**Status**: pending
**Prerequisites**: 없음
**Covers**: plan.md (Technical Context, 컨테이너 구성), quickstart.md, research.md (Decision 2, 4)

## 목표
Spring Boot 프로젝트 스캐폴딩, Docker Compose 인프라(PostgreSQL + PostGIS + Redis), Flyway 마이그레이션, jOOQ codegen 설정을 완료한다.

## Tasks

### 1.1 Spring Boot 프로젝트 생성
- `backend/` 디렉토리에 Gradle 기반 Spring Boot 3.x 프로젝트 생성
- Java 21 LTS 설정
- Dependencies: Spring Web, Spring Data JPA, Hibernate Spatial, jOOQ, Flyway, Spring Cache (Redis), Spring Boot Actuator, Micrometer Prometheus
- `application.yml` 기본 설정

### 1.2 Docker Compose 구성
- `docker-compose.yml` 작성
- Services: postgres-primary, postgres-replica-1, postgres-replica-2, redis, nginx, prometheus, grafana, frontend
- PostgreSQL 16 + PostGIS 3.4 이미지 사용
- Streaming Replication 설정 (Primary → Replica 2대)
- Redis 7 설정

### 1.3 Flyway 마이그레이션 — V1__init.sql
- data-model.md의 DDL을 Flyway 마이그레이션으로 작성
- `CREATE EXTENSION IF NOT EXISTS postgis`
- owners, businesses, business_hours, business_photos 테이블 생성
- GIST 인덱스, CHECK 제약 조건, Trigger 포함
- EC-3 대응: `UNIQUE(owner_id, name, latitude, longitude)` 제약 조건 추가

### 1.4 jOOQ Code Generation 설정
- `build.gradle`에 jOOQ codegen 플러그인 설정
- Flyway 마이그레이션 후 jOOQ 코드 자동 생성
- 생성 코드 출력 위치: `backend/src/main/java/com/proximity/adapter/out/persistence/jooq/`

### 1.5 DataSource 설정 (Read/Write 분리)
- `DataSourceConfig.java`: `AbstractRoutingDataSource`로 Primary/Replica 라우팅
- `@Transactional(readOnly = true)` 기반 자동 라우팅
- HikariCP Primary pool + Replica pool 분리

### 1.6 jOOQ DSLContext 설정
- `JooqConfig.java`: Replica DataSource에 바인딩된 `DSLContext` Bean 생성

## TDD 접근

### 테스트 우선 작성
- **T-1.1**: Docker Compose 인프라 기동 후 PostgreSQL PostGIS extension 확인 테스트 (Testcontainers)
- **T-1.2**: Flyway 마이그레이션 성공 확인 테스트 — 테이블 존재 여부, GIST 인덱스 존재 여부
- **T-1.3**: DataSource 라우팅 테스트 — `readOnly=true` 시 Replica, `readOnly=false` 시 Primary 연결 확인
- **T-1.4**: jOOQ DSLContext Bean 생성 확인 테스트

## 완료 기준
- [ ] `./gradlew build` 성공
- [ ] `docker compose up -d` 로 전체 인프라 기동
- [ ] Flyway 마이그레이션 성공, 테이블 생성 확인
- [ ] jOOQ 코드 생성 확인
- [ ] Read/Write DataSource 라우팅 테스트 통과

## 관련 Spec 항목
- research.md Decision 2 (PostgreSQL + PostGIS + Streaming Replication)
- research.md Decision 4 (Hexagonal Architecture — 컨테이너 구성)
- research.md Decision 7 (CQRS — jOOQ DataSource 설정)
- data-model.md (DDL, Trigger, Indexes)
- EC-3 (중복 사업장 UNIQUE 제약)
