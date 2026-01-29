# Quickstart: 주변 사업장 검색 서비스 (Proximity Service)

## Prerequisites

- Docker & Docker Compose
- JDK 21+
- Gradle 8+

## 로컬 실행

### 1. 인프라 실행 (Docker Compose)

```bash
docker compose up -d postgres-primary postgres-replica-1 postgres-replica-2 redis
```

### 2. PostGIS Extension 활성화

Primary에 PostGIS extension이 자동으로 활성화되지 않은 경우:

```bash
docker compose exec postgres-primary psql -U proximity -d proximity -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### 3. 데이터 마이그레이션

```bash
./gradlew flywayMigrate
```

### 4. 샘플 데이터 적재

```bash
./gradlew bootRun --args='--spring.profiles.active=seed'
```

### 5. 백엔드 실행

```bash
./gradlew bootRun
```

### 6. 프론트엔드 실행

```bash
docker compose up -d frontend
```

### 7. 전체 스택 실행 (한 번에)

```bash
docker compose up -d
```

## 서비스 접속

| 서비스 | URL |
| ------ | --- |
| Frontend | http://localhost |
| API | http://localhost:8080/api |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

## 검증 시나리오

### 사업장 검색

```bash
curl "http://localhost:8080/api/search?latitude=37.5665&longitude=126.9780&radius=1"
```

### 사업장 상세 조회

```bash
curl "http://localhost:8080/api/businesses/1"
```

### 사업장 등록 (인증 필요)

```bash
curl -X POST http://localhost:8080/api/businesses \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"테스트 사업장","address":"서울시 강남구","latitude":37.4979,"longitude":127.0276}'
```

### 프론트엔드 지도 확인

브라우저에서 http://localhost 접속 후 다음 시나리오를 확인한다.

**1. 현재 위치 자동 획득 + 파란 마커**
- 접속 시 위치 권한 팝업 → 허용 → 지도가 현재 위치 중심으로 표시, 파란 마커 표시

**2. 위치 권한 거부 → 서울 시청 fallback (EC-6)**
- 위치 권한 거부 → 지도가 서울 시청(37.5665, 126.9780) 중심으로 표시, 회색 마커
- "위치 정보를 가져올 수 없습니다. 기본 위치(서울 시청)를 표시합니다." 메시지 확인

**3. 반경 검색 → Circle + 마커 + 리스트**
- 반경 1km 선택 → 검색 버튼 → 지도에 반경 Circle 표시, 범위 내 사업장 마커 표시, 사이드 패널에 리스트 표시
- 반경 5km로 변경 → 재검색 → Circle 확장, 줌 레벨 조정, 마커/리스트 업데이트

**4. 마커 클릭 → Popup + 상세 패널**
- 사업장 마커 클릭 → Popup에 사업장 이름/주소 요약 표시, 사이드 패널에 상세 정보(영업시간, 사진 등) 표시

**5. 관리자 모드 → 지도 클릭 → 등록**
- 관리 모드 전환 → 지도 클릭 → 클릭 지점에 임시 마커 표시, 좌표가 등록 폼에 자동 입력
- 사업장 정보 입력 → 등록 → 지도에 새 마커 추가

**6. Geohash 셀 Rectangle 시각화**
- Geohash 시각화 토글 → 현재 지도 영역의 Geohash 셀 경계가 Rectangle로 표시
- 셀 경계 근처 사업장이 다른 Geohash에 매핑되는 것을 시각적으로 확인

### 부하 테스트 (K6)

```bash
docker compose run k6 run /scripts/search-load-test.js
```
