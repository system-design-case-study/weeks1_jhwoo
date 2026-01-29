# Phase 10: Frontend (Leaflet 지도) + E2E 통합 테스트

**Status**: pending
**Prerequisites**: Phase 07, 08, 09 완료
**Covers**: research.md (Decision 6), spec.md (User Story 1~3 전체, EC-1~EC-7, SC-001~SC-006), quickstart.md

## 목표
Leaflet + OpenStreetMap 기반 지도 UI를 포함한 Vanilla HTML/JS SPA 프론트엔드를 구현하고, 전체 스택 E2E 테스트로 모든 User Story와 Success Criteria를 검증한다.

## Tasks

### 10.1 Leaflet 지도 기본 설정
- `frontend/index.html`: Leaflet CSS/JS CDN 로드 (SRI hash 포함), split-view 레이아웃 (지도 영역 + 사이드 패널)
- `frontend/style.css`: 지도 컨테이너, 사이드 패널, 반응형 레이아웃
- `frontend/js/map.js`: Leaflet 지도 초기화, OpenStreetMap 타일 레이어, LayerGroup 관리 (마커/Circle/Rectangle 그룹별 분리)

### 10.2 Geolocation API 통합
- `frontend/js/geolocation.js`:
  - Browser Geolocation API로 현재 위치 자동 획득
  - 성공 시: 지도 중심 이동 + 파란 마커 표시
  - EC-6 (권한 거부/타임아웃): 서울 시청(37.5665, 126.9780) fallback + 회색 마커 + 안내 메시지
  - EC-7 (HTTPS 미사용): `window.isSecureContext` 확인, fallback 처리 + 안내 메시지

### 10.3 검색 + 지도 연동
- `frontend/js/search.js`:
  - 반경 선택 UI (0.5km, 1km, 2km, 5km, 20km 드롭다운)
  - 검색 버튼 → API 호출 → 결과를 지도 + 사이드 패널에 동시 렌더링
  - 지도: 반경 Circle 표시, 사업장 마커 배치, 줌 레벨 자동 조정
  - 사이드 패널: 사업장 리스트 (이름, 주소, 거리)
  - 반경 변경 시: 기존 Circle/마커 제거 → 새 결과로 교체
  - EC-1: 빈 결과 → 빈 Circle + "주변에 등록된 사업장이 없습니다. 반경을 넓혀보세요." 메시지
  - FR-004: 재검색 없이 지도 이동 시 이전 마커/Circle 유지

### 10.4 상세 보기
- `frontend/js/detail.js`:
  - 마커 클릭 또는 리스트 항목 선택 → Popup(지도) + 사이드 패널에 상세 정보
  - 상세 정보: 이름, 주소, 영업시간, 사진
  - 사진 미등록: `assets/no-photo.svg` 기본 이미지 또는 "등록된 사진이 없습니다" 안내
  - EC-5: 상세 조회 404 → 리스트에서 해당 항목 자동 제거 + 안내 메시지

### 10.5 관리자 모드
- `frontend/js/admin.js`:
  - 모드 전환 (검색 ↔ 관리)
  - 관리 모드에서 지도 클릭 → 임시 마커 + 좌표 자동 입력 (등록 폼)
  - 등록: 폼 입력 → POST API → 성공 시 지도에 새 마커 추가
  - 수정: 기존 마커 클릭 → 수정 폼 → PUT API → 마커 업데이트
  - 삭제: 기존 마커 클릭 → 삭제 확인 → DELETE API → 마커 제거
  - 비소유주 접근 시: 403 오류 처리 + 안내 메시지

### 10.6 Geohash 셀 시각화
- `frontend/js/geohash-viz.js`:
  - Geohash 셀 경계를 Leaflet Rectangle로 시각화
  - 토글 버튼으로 활성화/비활성화
  - 현재 지도 영역의 Geohash 정밀도별 셀 표시
  - 셀 경계 문제 시각적 학습 도구

### 10.7 API 호출 모듈
- `frontend/js/api.js`:
  - Backend API fetch 래퍼 (GET/POST/PUT/DELETE)
  - 공통 에러 처리 (400, 403, 404, 409, 429)
  - JWT 토큰 관리 (Authorization 헤더)
  - 응답 파싱 및 에러 코드별 메시지 매핑

### 10.8 Nginx 설정
- `nginx/nginx.conf`:
  - `/` → frontend static files
  - `/api/` → backend proxy
  - Rate Limiting: IP당 분당 60회 (EC-4)

### 10.9 E2E 통합 테스트 — User Story 1 (검색 + 지도)
- **US1-S1**: 접속 + 위치 권한 허용 → 지도가 현재 위치 중심, 파란 마커 표시
- **US1-S2**: 반경 1km 검색 → Circle + 마커 + 리스트 동시 표시
- **US1-S3**: 반경 변경 (1km → 5km) → Circle 확장, 줌 조정, 마커/리스트 업데이트
- **US1-S4**: 반경 0.5km, 사업장 없음 → 빈 Circle + 안내 메시지 (EC-1)
- **US1-S5**: 재검색 없이 지도 이동 → 이전 마커/Circle 유지 (FR-004)

### 10.10 E2E 통합 테스트 — User Story 2 (상세 조회 + Popup)
- **US2-S1**: 마커 클릭 또는 리스트 선택 → Popup + 사이드 패널에 상세 정보
- **US2-S2**: 사진 미등록 사업장 → 기본 이미지 또는 사진 없음 안내

### 10.11 E2E 통합 테스트 — User Story 3 (관리 + 지도 클릭)
- **US3-S1**: 관리 모드 + 지도 클릭 → 좌표 자동 입력 + 임시 마커
- **US3-S2**: 폼 입력 + 등록 → 새 마커 추가, 24시간 내 검색 반영
- **US3-S3**: 기존 마커 클릭 → 수정 폼 → 수정 완료
- **US3-S4**: 기존 마커 클릭 → 삭제 → 마커 제거
- **US3-S5**: 비소유주 수정/삭제 시도 → 403

### 10.12 E2E 통합 테스트 — Edge Cases
- **EC-1**: 빈 결과 검색 → 200 + 빈 Circle + 안내 메시지
- **EC-2**: 유효하지 않은 좌표 → 400
- **EC-3**: 중복 사업장 등록 → 409
- **EC-4**: Rate Limit 초과 → 429
- **EC-5**: 삭제 후 상세 조회 → 404 + 리스트 자동 제거
- **EC-6**: Geolocation API 실패 → 서울 시청 fallback + 안내 메시지
- **EC-7**: HTTPS 미사용 → 기본 좌표 fallback + 안내 메시지

### 10.13 Success Criteria 검증
- **SC-001**: 검색 p95 <1초 → K6 부하 테스트로 확인
- **SC-002**: 동시 1,000명 검색 → 성능 저하 없음
- **SC-003**: 사업장 변경 후 24시간 이내 반영 → TTL 기반 확인
- **SC-004**: 상세 조회 p95 <0.5초
- **SC-005**: 거리 정확도 ±5% 이내 → PostGIS 결과 vs Haversine 계산 비교
- **SC-006**: 피크 트래픽(5배) p99 <2초

### 10.14 Geohash vs PostGIS 비교 테스트
- 동일 좌표에 대해 `/api/search`와 `/api/search/geohash` 결과 비교
- Geohash 셀 경계 근처에서 PostGIS에만 포함되는 사업장 식별
- 프론트엔드 Geohash Rectangle 시각화와 연동하여 셀 경계 문제 확인

## TDD 접근

### 테스트 우선 작성

- **T-10.1**: Geolocation API mock 테스트
  - 성공 시: 좌표 반환 확인, 지도 중심 이동
  - 실패 시 (PERMISSION_DENIED): fallback 좌표(37.5665, 126.9780) 반환
  - 타임아웃: fallback 동작 확인
- **T-10.2**: 마커 생성/제거 테스트
  - 검색 결과 기반 마커 생성 (개수, 위치 일치)
  - 재검색 시 기존 마커 제거 + 새 마커 생성
  - LayerGroup clear/add 동작 검증
- **T-10.3**: Circle 반경 변경 테스트
  - 반경 값별 Circle 크기 일치 (0.5km=500m, 1km=1000m, ...)
  - 반경 변경 시 Circle 교체 확인
- **T-10.4**: 관리자 모드 지도 클릭 → 좌표 추출 테스트
  - 지도 클릭 이벤트 → latlng 좌표 추출
  - 임시 마커 생성 확인
  - 등록 폼 좌표 필드 자동 입력 확인
- **T-10.5**: API 응답별 UI 상태 테스트
  - 200 (정상): 마커/리스트 렌더링
  - 200 (빈 결과): EC-1 안내 메시지 표시
  - 400: 유효하지 않은 입력 오류 메시지
  - 403: 권한 없음 오류 메시지
  - 404: 리스트에서 항목 제거 + 안내
  - 409: 중복 등록 오류 메시지
  - 429: Rate Limit 초과 오류 + 재시도 안내
- **T-10.6**: Full Stack E2E 테스트 (Docker Compose 기동)
  - `docker compose up -d` → 전체 서비스 기동
  - curl/httpie로 API 시나리오 자동 실행
  - US1~US3 Acceptance Scenario 전체 통과 확인
- **T-10.7**: Geohash vs PostGIS 비교 테스트
  - 동일 좌표에 대해 `/api/search`와 `/api/search/geohash` 결과 비교
  - Geohash 셀 경계 근처에서 PostGIS에만 포함되는 사업장 식별
- **T-10.8**: Performance 테스트 (K6)
  - SC-001, SC-002, SC-004, SC-006 수치 검증
- **T-10.9**: 거리 정확도 테스트 (SC-005)
  - 알려진 좌표 쌍의 Haversine 거리 계산
  - API 반환 distance와 비교 → ±5% 이내 확인

## 완료 기준
- [ ] Leaflet 지도 + OpenStreetMap 타일 정상 렌더링
- [ ] Geolocation API 자동 획득 + EC-6/EC-7 fallback 동작
- [ ] 검색 → Circle + 마커 + 사이드 리스트 동시 표시
- [ ] 마커 클릭 → Popup + 상세 패널 표시
- [ ] 관리자 모드 → 지도 클릭 좌표 입력 → CRUD 동작
- [ ] Geohash 셀 Rectangle 시각화 동작
- [ ] Nginx 프록시 + Rate Limiting 동작
- [ ] User Story 1~3 Acceptance Scenario 전체 E2E 통과
- [ ] Edge Cases EC-1 ~ EC-7 전체 E2E 통과
- [ ] Success Criteria SC-001 ~ SC-006 검증 통과
- [ ] `docker compose up -d`로 전체 스택 기동 확인
- [ ] quickstart.md의 모든 검증 시나리오 실행 가능

## 관련 Spec 항목
- spec.md User Story 1, 2, 3 Acceptance Scenario 전체
- spec.md Edge Cases EC-1 ~ EC-7 전체
- spec.md Success Criteria SC-001 ~ SC-006 전체
- spec.md FR-001 ~ FR-010 전체
- research.md Decision 6 (Vanilla HTML/JS SPA + Leaflet/OpenStreetMap)
- quickstart.md 전체
