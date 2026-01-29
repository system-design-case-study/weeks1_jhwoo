# Git Commit Convention

Conventional Commits 형식을 따른다.

## 형식

```
<type>: <한글 제목>

- 작업 내용 bullet point
```

## Type

| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `docs` | 문서 변경 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드, 설정 등 기타 변경 |

## 규칙

- 제목과 본문은 한글로 작성
- 본문에 파일명을 적지 않는다
- 본문에는 **어떤 작업을 했는지** 행위 중심으로 작성한다
- Co-Authored-By, Generated with 등 추가 주석 사용하지 않음

## 예시

```
feat: 근접 검색 API 추가

- 위치 기반 반경 검색 엔드포인트 구현
- 거리 계산 로직 추가
- 검색 결과 캐싱 적용
```

```
fix: 토큰 만료 시 갱신 실패 수정

- 만료된 refresh token 처리 로직 보정
- 에러 응답 코드 통일
```

```
chore: gitignore 설정 정리

- IDE 설정 파일 추적 제외
- 빌드 산출물 무시 규칙 추가
```
