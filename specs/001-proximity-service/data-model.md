# Data Model: 주변 사업장 검색 서비스 (Proximity Service)

## Entity Relationship

```
┌──────────┐        ┌──────────────┐
│  Owner   │ 1───N  │   Business   │
│          │        │              │
│ id (PK)  │        │ id (PK)      │
│ email    │        │ owner_id(FK) │
│ password │        │ name         │
│ name     │        │ address      │
│          │        │ latitude     │
│          │        │ longitude    │
│          │        │ location     │
│          │        │ phone        │
│          │        │ category     │
│          │        │ created_at   │
│          │        │ updated_at   │
└──────────┘        └──────┬───────┘
                           │
                    ┌──────┴───────┐
                    │              │
               1───N│         1───N│
        ┌───────────┴──┐  ┌───────┴────────┐
        │BusinessHours │  │ BusinessPhoto  │
        │              │  │                │
        │ id (PK)      │  │ id (PK)        │
        │ business_id  │  │ business_id    │
        │ day_of_week  │  │ photo_url      │
        │ open_time    │  │ display_order  │
        │ close_time   │  │ created_at     │
        │ is_closed    │  │                │
        └──────────────┘  └────────────────┘
```

## Table Definitions

### owners

| Column | Type | Constraints | Description |
| ------ | ---- | ----------- | ----------- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 소유주 고유 식별자 |
| email | VARCHAR(255) | UNIQUE, NOT NULL | 로그인 이메일 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 해시된 비밀번호 |
| name | VARCHAR(100) | NOT NULL | 소유주 이름 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 가입 일시 |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 수정 일시 |

### businesses

| Column | Type | Constraints | Description |
| ------ | ---- | ----------- | ----------- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 사업장 고유 식별자 |
| owner_id | BIGINT | FK → owners.id, NOT NULL | 소유주 참조 |
| name | VARCHAR(255) | NOT NULL | 사업장 이름 |
| address | VARCHAR(500) | NOT NULL | 주소 |
| latitude | DECIMAL(9,6) | NOT NULL, CHECK(-90 ≤ val ≤ 90) | 위도 |
| longitude | DECIMAL(9,6) | NOT NULL, CHECK(-180 ≤ val ≤ 180) | 경도 |
| location | GEOGRAPHY(Point, 4326) | NOT NULL | PostGIS 공간 컬럼 (lat/lng로부터 자동 생성) |
| phone | VARCHAR(20) | | 전화번호 |
| category | VARCHAR(100) | | 업종 카테고리 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 등록 일시 |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 수정 일시 |

**Indexes:**
- `idx_business_location` USING GIST(location) — 공간 검색 최적화
- `idx_owner_id` (owner_id)

**Trigger:**
```sql
CREATE OR REPLACE FUNCTION update_business_location()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_business_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON businesses
    FOR EACH ROW
    EXECUTE FUNCTION update_business_location();
```

### business_hours

| Column | Type | Constraints | Description |
| ------ | ---- | ----------- | ----------- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 고유 식별자 |
| business_id | BIGINT | FK → businesses.id, NOT NULL | 사업장 참조 |
| day_of_week | SMALLINT | NOT NULL, CHECK(0 ≤ val ≤ 6) | 요일 (0=월 ~ 6=일) |
| open_time | TIME | | 영업 시작 시간 |
| close_time | TIME | | 영업 종료 시간 |
| is_closed | BOOLEAN | NOT NULL, DEFAULT FALSE | 정기 휴무 여부 |

**Indexes:**
- `idx_business_day` (business_id, day_of_week) UNIQUE

### business_photos

| Column | Type | Constraints | Description |
| ------ | ---- | ----------- | ----------- |
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 고유 식별자 |
| business_id | BIGINT | FK → businesses.id, NOT NULL | 사업장 참조 |
| photo_url | VARCHAR(1000) | NOT NULL | 사진 URL |
| display_order | INT | NOT NULL, DEFAULT 0 | 표시 순서 |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | 등록 일시 |

**Indexes:**
- `idx_business_order` (business_id, display_order)

## DDL (PostgreSQL 16 + PostGIS 3.4)

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE owners (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE businesses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES owners(id),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DECIMAL(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    location GEOGRAPHY(Point, 4326) NOT NULL,
    phone VARCHAR(20),
    category VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, name, latitude, longitude)
);

CREATE INDEX idx_business_location ON businesses USING GIST(location);
CREATE INDEX idx_owner_id ON businesses(owner_id);

CREATE TABLE business_hours (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    open_time TIME,
    close_time TIME,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (business_id, day_of_week)
);

CREATE TABLE business_photos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    photo_url VARCHAR(1000) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_business_order ON business_photos(business_id, display_order);
```

## Validation Rules

- **latitude**: -90.0 ~ 90.0 범위
- **longitude**: -180.0 ~ 180.0 범위
- **location**: latitude, longitude 변경 시 Trigger에 의해 자동 계산 (`GEOGRAPHY(Point, 4326)`)
- **day_of_week**: 0(월요일) ~ 6(일요일)
- **email**: RFC 5322 형식 검증
- **password**: 최소 4자, BCrypt 해시 저장

## Read Path Optimization

검색 시 `businesses` 테이블의 `location` 컬럼에 대해 `ST_DWithin`로 GIST 인덱스 스캔을 수행하여 목록을 반환한다. 상세 조회 시에만 `business_hours`, `business_photos`를 JOIN 또는 별도 쿼리로 가져온다. 이는 Read-heavy 환경에서 검색 쿼리의 부하를 최소화하기 위함이다.

읽기 쿼리는 **jOOQ DSL**로 실행되며, 쓰기 연산은 JPA를 통해 처리한다 (CQRS 패턴).

### jOOQ 검색 쿼리 예시

**Java DSL**:
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

**생성되는 SQL**:
```sql
SELECT id, name, address,
       ST_Distance(location, ST_MakePoint(?, ?)::geography) AS distance
FROM businesses
WHERE ST_DWithin(location, ST_MakePoint(?, ?)::geography, ?)
ORDER BY distance
LIMIT ? OFFSET ?
```

jOOQ의 type-safe DSL을 통해 테이블명, 컬럼명, PostGIS 함수 호출을 컴파일 타임에 검증하며, JPA `@Query` 기반 native query의 문자열 오류 가능성을 제거한다.
