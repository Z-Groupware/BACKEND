<div align="center">

# Z · Groupware Backend

**회의의 말을, 조직의 실행과 기억으로**

회의 → 결정 → 액션 → 프로젝트 → 인수인계

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-8.8-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Flyway](https://img.shields.io/badge/Flyway-120%20migrations-CC0200?logo=flyway&logoColor=white)](https://flywaydb.org/)
[![AWS](https://img.shields.io/badge/AWS-S3%20·%20Transcribe%20·%20EC2-232F3E?logo=amazonwebservices&logoColor=white)](https://aws.amazon.com/)
[![Docker](https://img.shields.io/badge/Docker-compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

</div>

---

## 목차

- [무엇을 만들었나](#무엇을-만들었나)
- [핵심 파이프라인](#핵심-파이프라인)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [도메인 지도](#도메인-지도)
- [API 개요](#api-개요)
- [데이터베이스](#데이터베이스)
- [보안](#보안)
- [AI 코드 리뷰 루프](#ai-코드-리뷰-루프)
- [CI/CD](#cicd)
- [로컬에서 실행하기](#로컬에서-실행하기)
- [테스트](#테스트)
- [문서 지도](#문서-지도)
- [개발 규약](#개발-규약)

---

## 무엇을 만들었나

회의에서 오간 말은 대부분 회의실을 나가는 순간 사라진다. 누가 무엇을 언제까지 하기로 했는지는
각자의 기억과 메모에 흩어지고, 담당자가 퇴사하면 그 맥락은 통째로 증발한다.

**Z** 는 그 흐름을 하나의 파이프라인으로 잇는 AI 네이티브 그룹웨어다.

| 단계 | 하는 일 |
|---|---|
| **캡처** | 대면·비대면 회의의 음성을 청크 단위로 수집하고 실시간 자막을 흘려보낸다 |
| **분석** | 음성을 텍스트로 바꾸고, 9단계 분석 계층이 주제·결정·담당자를 뽑아낸다 |
| **분배** | 추출된 `(담당자, 할 일, 기한)` 튜플을 실제 액션 카드로 사람 보드에 꽂는다 |
| **추적** | 액션이 프로젝트·팀 대시보드에서 진행 상태로 굴러간다 |
| **인수인계** | 퇴사·휴직 버튼 한 번에, 출처 회의까지 붙은 인수인계서가 자동 조립된다 |
| **과금** | seat 이 아니라 **실제 AI 토큰 사용량** 기준으로 원가를 계량한다 |

### 규모

| | |
|---|---|
| 프로덕션 Java 파일 | **1,320개** (83,825 라인) |
| 테스트 파일 | **345개** (63,776 라인) |
| REST 엔드포인트 | **143개** (컨트롤러 47개) |
| 도메인 | **15개** |
| Flyway 마이그레이션 | **120개** |
| 에러 코드 | **182개** (도메인 접두어 15종) |
| 커밋 · 기여자 | **1,146 커밋 · 6명** (`develop` 기준) |

---

## 핵심 파이프라인

회의 하나가 액션 카드가 되기까지의 전체 경로다.

```mermaid
flowchart TD
    subgraph CAP["cap · 수집"]
        A1[회의 시작<br/>capture_session] --> A2[오디오 청크<br/>presigned PUT → S3]
        A2 --> A3[실시간 자막<br/>SSE 스트림]
        A2 --> A4[ffmpeg 조립<br/>recording_part → recording]
    end

    subgraph STT["capture · 전사"]
        A4 --> B1[stt_block 분할]
        B1 --> B2[AWS Transcribe<br/>잡 제출]
        B2 --> B3[결과 JSON → S3<br/>stt-out/]
    end

    subgraph AI["capture · 분석 계층"]
        B3 --> L1["L1 화자 귀속 · 코드"]
        L1 --> L15["L1.5 지시어 해소"]
        L15 --> L2["L2 주제 분할"]
        L2 --> L3["L3 주제별 정리"]
        L3 --> L35["L3.5 확정/논의 게이트"]
        L35 --> L4["L4 assignment tuple 추출"]
        L4 --> L5["L5 관점 다변화 검증"]
        L5 --> L6["L6 규칙·모순 검사 · 코드"]
        L6 --> L7["L7 자동확정 게이트 · 코드"]
    end

    L7 --> D1[DIST · 액션 분배]
    D1 --> OV[OVERVIEW · 회의 개요]

    subgraph OUT["결과"]
        D1 --> R1[action 보드]
        R1 --> R2[project 타임라인]
        R1 --> R3[handover 자동 조립]
        L4 -.토큰 사용량.-> R4[metering 원장]
    end
```

L1·L6·L7 은 LLM 을 부르지 않는 **코드 계층**이다. 나머지 6개가 AI EC2(Python 서버)를 호출한다.

### 이 파이프라인의 설계 결정 몇 가지

- **`analysis_layer` 에 `UNIQUE(meeting_id, layer)`** — 큐가 at-least-once 라 같은 회의가 두 번
  들어오는 일은 언젠가 반드시 일어난다. 그때 만들어지는 것이 사람 보드에 꽂히는 액션이므로,
  중복 분배를 DB 제약으로 막는다.
- **`DIST` 와 `OVERVIEW` 는 계층 번호를 받지 않는다** — 모델을 부르지 않는 단계라 `L1~L7` 과
  같은 줄에 세우면 AI 계층이 하나 더 있는 것처럼 읽힌다.
- **`OVERVIEW` 는 완료 판정에서 빠진다** — 개요 생성 실패가 회의 전체를 「미완」으로 만들면
  재실행이 **모든 계층의 토큰을 다시 태운다**. 표시용 문장 하나 때문에.
- **L7 자동확정 게이트는 모델이 말한 확신도를 쓰지 않는다** — 코드가 4개 조건을 직접 검사한다.

---

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| **런타임** | Java 17 (Temurin) · Spring Boot 4.1.0 · Gradle |
| **웹** | Spring MVC · springdoc-openapi 3.0.3 (Swagger UI) · SSE |
| **영속성** | Spring Data JPA · MySQL 8 · Flyway · HikariCP |
| **캐시·세션** | Redis 8.8 (리프레시 토큰 스토어 · 레이트 리밋 카운터) |
| **인증** | Spring Security · jjwt 0.12.6 (HS256) · BCrypt |
| **AWS** | S3 (녹음·첨부·STT I/O) · Transcribe (STT) · EC2 · SSM Parameter Store |
| **AI** | 사내 Python AI 서버 (RestClient + `X-Internal-Token`) · Anthropic Java SDK (리뷰 루프) |
| **미디어** | ffmpeg (녹음 청크 조립 · STT 블록 오디오 추출) |
| **관측** | Actuator · Micrometer · Prometheus · Grafana (대시보드·알림 프로비저닝 포함) |
| **테스트** | JUnit 5 · Spring Boot Test · **ArchUnit** · **Testcontainers** (실 MySQL 마이그레이션 검증) |
| **품질 게이트** | Semgrep · ShellCheck · Trivy · Dependency Review · 자체 LLM 리뷰 루프 |

### JWT 를 직접 다루는 이유

Spring OAuth2 리소스 서버 대신 `jjwt` 를 쓴다. 에러 응답을 프로젝트 공용
`ErrorResponse` 형식(`errorCode` · `traceId` 포함)으로 직접 제어해야 하기 때문이다.

---

## 아키텍처

**도메인별 헥사고날(포트-어댑터)** 구조다. 각 도메인이 자기 안에서 4개 레이어를 갖는다.

```
com.module06.backend.<domain>/
├── presentation/          ← 컨트롤러 · Request/Response DTO
│   └── api/
├── application/           ← 유스케이스 · 서비스 · Command · Port
│   ├── usecase/           ←   인바운드 포트 (인터페이스)
│   ├── service/           ←   구현
│   ├── command/
│   └── port/out/          ←   아웃바운드 포트 (인터페이스)
├── domain/                ← 순수 도메인
│   ├── model/             ←   POJO — Spring·JPA 의존 금지
│   ├── repository/
│   └── exception/
└── infrastructure/        ← 어댑터 (JPA · S3 · AI · SSE · ffmpeg …)
    ├── persistence/
    └── adapter/
```

### 이 구조를 ArchUnit 이 강제한다

[`ArchitectureRulesTest`](src/test/java/com/module06/backend/architecture/ArchitectureRulesTest.java) 가
push 게이트(Gate 1)에서 실행된다.

| 규칙 | 내용 | 깨지면 생기는 일 |
|---|---|---|
| `ARCH_001` | `presentation` → `domain.repository` 직접 참조 금지 | 트랜잭션 경계·유스케이스가 컨트롤러로 샌다 |
| `ARCH_002` | `domain.model` → Spring·JPA 의존 금지 | 단위 테스트가 컨테이너를 요구하고 이식성이 사라진다 |
| `ARCH_003` | `application` → `infrastructure` 직접 참조 금지 | 의존 방향이 뒤집혀 구현 교체가 불가능해진다 |
| (추가) | 도메인 패키지 간 순환 참조 금지 | 레이어를 나눠도 결국 한 덩어리다 |

> **규칙이 벙어리가 아님을 증명한다.** `RuleActuallyFires` 중첩 테스트가 *의도적 위반 픽스처*를
> 넣고 규칙이 **실제로 실패하는지** 검증한다. "레이어가 없어서 통과"와 "규칙이 잘못 짜여서
> 아무것도 안 잡음"을 구분할 수 없으면, 통과하는 게이트는 아무 의미가 없다.

### 응답 계약

모든 성공 응답은 `ApiResponse<T>` 로 감싼다.

```jsonc
// 성공
{ "httpStatus": 200, "message": "조회 성공", "data": { /* ... */ } }
```

```jsonc
// 실패 — GlobalExceptionHandler
{
  "errorCode": "AU-003",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "timestamp": "2026-08-20T14:03:11.482",
  "path": "/api/auth/login",
  "traceId": "3f9c1a…",
  "details": [{ "field": "email", "reason": "형식이 올바르지 않습니다" }]
}
```

`traceId` 는 `TraceIdFilter` 가 요청마다 심고 로그 MDC 와 응답에 같이 실린다 — 사용자가 보낸
에러 화면 하나로 서버 로그를 바로 찾을 수 있다.

---

## 도메인 지도

```mermaid
flowchart LR
    identity["identity · 인증·조직"]
    meeting["meeting · 회의 생명주기"]
    cap["cap · 녹음·자막 수집"]
    capture["capture · STT·AI 분석"]
    action["action · 액션 보드"]
    project["project · 프로젝트"]
    handover["handover · 인수인계"]
    metering["metering · 토큰 과금"]
    meetingroom["meetingroom · 회의실"]
    calendar["calendar"]
    notification["notification"]

    identity --> meeting --> cap --> capture --> action
    action --> project
    action --> handover
    capture -.토큰 사용량.-> metering
    meetingroom --> meeting
    action --> calendar
    meeting --> notification
```

| 도메인 | 파일 | API | 책임 |
|---|---:|---:|---|
| **capture** | 242 | 18 | STT 블록 · 분석 계층 오케스트레이션 · 검토/확정 · 요약 · 품질·원가 측정 |
| **identity** | 216 | 33 | 로그인/토큰 재발급/로그아웃 · 회사 등록·온보딩 · 구성원·팀·직급·역할 CRUD · 비밀번호 |
| **meeting** | 168 | 15 | 회의 예약·수정·취소·완료 · 참석자 · 캡처 세션 일시정지/재개 · 각종 대시보드 목록 |
| **cap** | 148 | 13 | 청크 presign 업로드 · ffmpeg 조립 · 수동/온라인 녹음 등록 · 재생 URL · **자막 SSE** |
| **metering** | 124 | 14 | 토큰 사용량 원장 · 쿼터 강제 · 스토리지 플랜 · 구독·결제수단 · 부서별 원가 대시보드 |
| **action** | 68 | 13 | 액션 상세·타임라인 · 팀/회사 액션 조회 · 일괄 상태 변경 · 첨부 다운로드 |
| **project** | 63 | 11 | 프로젝트 CRUD · 타임라인 · S3 첨부(업로드/확정/삭제) · 오너 대시보드 |
| **meetingroom** | 62 | 5 | 회의실 CRUD · 슬롯 기반 가용성 조회 |
| **reviewloop** | 58 | – | *(아래 [AI 코드 리뷰 루프](#ai-코드-리뷰-루프) 참조)* |
| **handover** | 54 | 11 | 인수인계 생성·재배정·중간승인·최종승인·반려 · 패키지 조립 · 인사이트 |
| **notice** | 35 | 5 | 공지 CRUD |
| **notification** | 28 | 1 | **알림 SSE** · 회의 리마인더 |
| **global** | 24 | – | 보안·예외·응답·감사로그·레이트리밋 공용 |
| **calendar** | 19 | 3 | 캘린더 뷰(회의+액션+할일 병합) · 개인 할일 |
| **search** | 10 | 1 | 통합 검색 (JDBC 직접 조회) |

### `cap` 과 `capture` 는 다른 도메인이다

이름이 비슷해서 헷갈리기 쉽다.

- **`cap`** — 음성을 **모으는** 쪽. presigned 업로드, ffmpeg 조립, 실시간 자막 SSE.
- **`capture`** — 모은 것을 **읽는** 쪽. Transcribe 제출, L1~L7 분석, 액션 분배, 요약.

---

## API 개요

전체 143개. Swagger UI 는 `http://localhost:8080/swagger-ui.html` 에서 볼 수 있다.

<details>
<summary><b>인증 · 조직 (identity) — 33개</b></summary>

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/auth/login` | 기업코드 + 이메일 + 비밀번호 로그인 |
| `POST` | `/api/auth/refresh` | 리프레시 토큰 회전 재발급 |
| `POST` | `/api/auth/password/reset` | 비밀번호 찾기 (공개) |
| `POST` | `/api/auth/logout` | 갱신표 폐기 |
| `GET` `PATCH` | `/api/auth/me` | 내 정보 조회·수정 |
| `PATCH` | `/api/auth/me/password` | 셀프 비밀번호 변경 |
| `POST` | `/api/companies/lookup` | 기업코드 조회 (공개) |
| `POST` | `/api/companies/registrations` | 회사 + 오너 계정 생성 (공개) |
| `POST` | `/api/companies/me/onboarding` | 온보딩 (팀·직급·구성원 일괄) |
| `GET` `PATCH` | `/api/companies/me` | 회사 프로필 |
| `GET` | `/api/members/org-chart` | 조직도 |
| `GET` | `/api/members/my-team` | 내 팀 로스터 |
| `GET` | `/api/members/dashboard-summary` | 구성원 대시보드 요약 |
| `GET` | `/api/members/leaders-status` | 팀장 현황 |
| `GET` `PATCH` | `/api/members/{memberId}` | 구성원 상세·수정 |
| `PATCH` | `/api/members/{memberId}/admin` | 어드민 권한 토글 (오너 전용) |
| `DELETE` | `/api/manage/members/{memberId}` | 구성원 삭제 (soft) |
| `GET` `POST` `PATCH` `DELETE` | `/api/teams`, `/api/teams/{id}` | 부서 CRUD |
| `GET` `POST` `PATCH` `DELETE` | `/api/teams/{teamId}/roles` | 부서 내 역할 CRUD |
| `GET` `POST` `PATCH` `DELETE` | `/api/job-positions` | 직급 CRUD |

</details>

<details>
<summary><b>회의 (meeting · meetingroom) — 20개</b></summary>

| Method | Path | 설명 |
|---|---|---|
| `POST` `GET` | `/api/meetings` | 회의 예약 · 목록 |
| `POST` | `/api/meetings/online` | 비대면 회의 생성 |
| `GET` `PATCH` `DELETE` | `/api/meetings/{meetingId}` | 상세 · 수정 · 취소 |
| `POST` | `/api/meetings/{meetingId}/complete` | 회의 종료 |
| `POST` | `/api/meetings/{meetingId}/capture-session/pause` `/resume` | 캡처 일시정지·재개 |
| `PUT` | `/api/meetings/{meetingId}/attendees` | 참석자 교체 |
| `GET` | `/api/meetings/dashboard` `/upcoming` | 대시보드 · 다가오는 회의 |
| `GET` | `/api/meetings/pending-action-distributions` | 분배 대기 회의 |
| `GET` | `/api/meetings/stalled-summaries` | 요약이 멈춘 회의 |
| `GET` `POST` `PATCH` `DELETE` | `/api/rooms` | 회의실 CRUD |
| `GET` | `/api/rooms/availability` | 슬롯 가용성 |

</details>

<details>
<summary><b>캡처 · 분석 (cap · capture) — 31개</b></summary>

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/meetings/{meetingId}/parts/presign` | 청크 업로드 URL 발급 |
| `POST` | `/api/meetings/{meetingId}/parts/{seq}/complete` | 청크 업로드 완료 통보 (202) |
| `GET` | `/api/meetings/{meetingId}/parts/status` | 업로드 진행 상태 |
| `POST` `GET` | `/api/meetings/{meetingId}/captions` | 자막 제출 · 조회 |
| `GET` | `/api/meetings/{meetingId}/captions/stream` | **자막 실시간 SSE** |
| `POST` | `/api/meetings/online/recordings/upload-url` | 비대면 녹음 업로드 URL |
| `POST` | `/api/meetings/{meetingId}/recordings/manual` | 수동 녹음 등록 |
| `GET` | `/api/meetings/{meetingId}/recordings/playback-url` | 재생 URL |
| `GET` | `/api/captures/active` | 진행 중 캡처 |
| `POST` | `/api/meetings/{meetingId}/analysis` `/analysis/retry` | 분석 실행 · 재시도 |
| `GET` | `/api/meetings/{meetingId}/processing-status` | 계층별 진행 상태 |
| `GET` `PATCH` `POST` `DELETE` | `/api/meetings/{meetingId}/review…` | 액션 검토·수정·추가·삭제·확정 |
| `GET` `PATCH` | `/api/meetings/{meetingId}/summary` | 요약 조회·수정 |
| `GET` | `/api/meetings/{meetingId}/transcripts` | 전사본 |
| `GET` `POST` | `/api/meetings/{meetingId}/stt-blocks` | STT 블록 조회 · 재시도 |
| `GET` `POST` | `/api/meetings/{meetingId}/vocabulary` | 회의 어휘집 조회 · 재구축 |
| `POST` `GET` | `/api/quality/gold-set`, `/api/quality/metrics`, `/api/quality/cost` | 품질 골드셋 · 지표 · 원가 |

</details>

<details>
<summary><b>실행 · 인수인계 · 과금 (action · project · handover · metering) — 49개</b></summary>

| Method | Path | 설명 |
|---|---|---|
| `POST` `GET` | `/api/actions`, `/api/actions/{actionId}` | 액션 생성 · 상세 |
| `PATCH` | `/api/actions/complete/bulk` | 일괄 완료 |
| `GET` | `/api/team/actions/{id}?tab=timeline` | 팀 액션 타임라인 |
| `GET` | `/api/team/actions/dashboard-summary` | 팀 대시보드 |
| `GET` | `/api/company/actions` | 회사 전체 액션 |
| `GET` `POST` `PATCH` | `/api/projects` … | 프로젝트 CRUD · 타임라인 · 일괄 상태 |
| `POST` `GET` `DELETE` | `/api/projects/{id}/attachments…` | 첨부 업로드 URL · 확정 · 다운로드 · 삭제 |
| `POST` `GET` | `/api/handovers`, `/api/handovers/{id}` | 인수인계 생성 · 상세 |
| `GET` | `/api/handovers/{id}/package` `/insights` | **패키지 자동 조립** · 인사이트 |
| `PATCH` | `/api/handovers/{id}/items/{actionId}/reassign` | 인수자 재배정 |
| `PATCH` | `/api/handovers/{id}/complete` `/finalize` `/reject` | 중간승인 · 최종승인 · 반려 |
| `GET` `PUT` | `/api/metering/plan` `/storage-plan` `/dashboard` | 토큰·스토리지 플랜 · 원가 대시보드 |
| `GET` `POST` | `/api/companies/me/billing`, `/subscription/pay`, `/payment-methods` | 청구·결제 |
| `GET` `DELETE` | `/api/companies/me/storage` | 스토리지 현황 · 프로젝트별 삭제 |

</details>

<details>
<summary><b>기타 (notice · notification · calendar · search) — 10개</b></summary>

| Method | Path | 설명 |
|---|---|---|
| `GET` `POST` `PUT` `DELETE` | `/api/notices` | 공지 CRUD |
| `GET` | `/api/notifications/stream` | **알림 실시간 SSE** |
| `GET` | `/api/calendar` | 회의+액션+할일 병합 캘린더 |
| `POST` `PATCH` | `/api/todos`, `/api/todos/{id}/complete` | 개인 할일 |
| `GET` | `/api/v1/search` | 통합 검색 |

</details>

### 에러 코드

도메인 접두어 + 3자리. 총 182개.

| 접두어 | 도메인 | 개수 |
|---|---|---:|
| `AU` | 인증·인가·조직 | 46 |
| `HO` | 인수인계 | 29 |
| `MT` | 회의 | 27 |
| `CAP` | 캡처 수집 | 25 |
| `AC` | 액션 | 15 |
| `BIL` | 청구 | 10 |
| `ANLZ` | 분석 파이프라인 | 8 |
| `PJ` `CS` `MR` `STT` `NT` `LV` `SR` `CAL` | 프로젝트·캡처세션·회의실·STT·알림·휴직·검색·캘린더 | 30 |

---

## 데이터베이스

**스키마의 주인은 Flyway다.** `ddl-auto` 는 `validate` 이상으로 올리지 않는다.

```yaml
spring.jpa.hibernate.ddl-auto: ${JPA_DDL_AUTO:validate}
spring.flyway:
  validate-on-migrate: true                    # checksum 불일치(=적용된 파일 수정) 시 부팅 실패
  out-of-order: ${FLYWAY_OUT_OF_ORDER:true}    # 개발은 허용, 운영은 false
  baseline-on-migrate: false                   # 켜면 반쪽짜리 스키마가 조용히 통과한다
```

### 담당자별 버전 레인

`V2.2.x` · `V2.3.x` · `V2.6.x` · `V3.x` · `V5.x` … 처럼 담당자마다 버전 영역을 나눠 쓴다.
서로 다른 브랜치의 마이그레이션이 순서 무관하게 병합되도록 **개발 환경에서만** `out-of-order` 를 켠다.
운영은 "머지 순서 = 배포 순서"를 강제한다.

자세한 규칙: **[docs/DB_MIGRATION_RULES.md](docs/DB_MIGRATION_RULES.md)**

### 주요 테이블

| 그룹 | 테이블 |
|---|---|
| 조직 | `company` `member` `team` `role` `position` `password_history` |
| 회의 | `meeting` `meeting_attendee` `meeting_topic` `meeting_room` `meeting_room_slot` `capture_session` |
| 캡처 | `recording` `recording_part` `capture_upload_state` `caption_chunk` `transcript_chunk` |
| 분석 | `stt_block` `stt_gap` `analysis_layer` `analysis_layer_artifact` `meeting_analysis_run` `meeting_assignment_tuple` `meeting_tuple_vector` `meeting_vocabulary` `meeting_summary` `meeting_decision` `quality_gold_set` |
| 실행 | `action` `action_checklist_item` `project` `project_team` `project_attachment` `personal_todo` `review_log` |
| 인수인계 | `handover` `handover_item` `handover_insight` |
| 과금 | `subscription` `billing_history` `payment_method` `company_billing_config` `company_token_plan` `token_usage_record` `token_usage_outbox` `company_storage_plan` `meeting_storage_usage` `meeting_text_storage_usage` |
| 기타 | `notice` `notification` |

### 스키마 정합성은 CI 가 실 MySQL 로 검증한다

로컬·CI 단위 테스트는 H2 로 돌기 때문에 MySQL 전용 DDL 실패를 재현하지 못한다.
그래서 별도 태스크가 **Testcontainers 로 진짜 MySQL 을 띄워** Flyway 를 전부 적용하고
`ddl-auto=validate` 로 엔티티-스키마 정합성을 확인한다.

```bash
./gradlew migrationCheck
```

---

## 보안

### 인증 흐름

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant RL as RateLimitFilter
    participant JF as JwtAuthenticationFilter
    participant SC as SecurityConfig
    participant SVC as 서비스
    participant R as Redis

    C->>RL: POST /api/auth/login
    RL->>RL: IP 60회/분 · 계정 실패 5회/5분
    RL->>SVC: 통과
    SVC->>SVC: BCrypt 검증
    SVC->>R: 리프레시 토큰 저장
    SVC-->>C: Access 30m + Refresh 1d(또는 14d)

    C->>RL: GET /api/… (Bearer)
    RL->>JF: 통과
    JF->>JF: 서명·만료 검증 → AuthPrincipal
    JF->>SC: SecurityContext 주입
    SC->>SC: anyRequest().authenticated()
    SC->>SVC: @PreAuthorize + 소유권 검사
```

### 기본이 "잠김"이다

```java
.anyRequest().authenticated()
```

전에는 체인이 `permitAll()` 로 끝나서, 담당자가 자기 엔드포인트를 등록 목록에 적어야만
익명 요청이 401 이 됐다. **등록을 빼먹으면 그 API 가 조용히 열렸다.**
지금은 방향이 뒤집혀서, 공개 예외를 빼먹으면 401 이 나 바로 발견된다.

공개 경로는 아래뿐이다.

| 경로 | 왜 공개인가 |
|---|---|
| `POST /api/auth/login` `/refresh` | 토큰을 받으러 오는 경로. 인증을 걸면 아무도 로그인할 수 없다 |
| `POST /api/auth/password/reset` | 비밀번호를 잃어버려 로그인을 못 하는 사람이 부른다 |
| `POST /api/companies/lookup` `/registrations` | 회사가 아직 없는 사람이 부른다 |
| `GET /api/billing-config` | 가입 전 요금제 화면 |
| `/actuator/health` `/actuator/prometheus` | LB·Prometheus 가 토큰 없이 수집한다 |
| `/swagger-ui/**` `/v3/api-docs/**` | 잠그면 Swagger UI 가 스펙을 읽지 못해 화면이 죽는다 |

### 권한 모델 — 겸직 모델

`authority ENUM(OWNER, LEADER, MEMBER)` + `is_admin BOOLEAN` **두 컬럼**이다.

**ADMIN 은 역할 값이 아니다.** 한 사람이 팀장(`LEADER`)이면서 동시에 어드민일 수 있고,
그때 역할을 `ADMIN` 으로 덮으면 원래 역할이 사라진다. `ROLE_ADMIN` authority 는
`is_admin = true` 에서만 심긴다.

```java
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")   // == authority == OWNER || isAdmin
```

실제로 쓰이는 인가 표현식 분포(총 131곳):

| 표현식 | 곳 | 의미 |
|---|---:|---|
| `hasAnyRole('OWNER','ADMIN','LEADER','MEMBER')` | 49 | 로그인한 정상 구성원 전체 |
| `isAuthenticated()` | 27 | 역할 무관, 소유권은 서비스가 검사 |
| `hasRole('OWNER')` | 22 | 오너 전용 (회사 설정·과금) |
| `hasAnyRole('OWNER','ADMIN')` | 19 | 관리 화면 (`/manage/*`) |
| `hasRole('OWNER') or principal.isAdmin()` | 4 | 위와 같은 뜻을 명시적으로 쓴 자리 |
| `hasAnyRole('LEADER','OWNER','ADMIN')` 외 | 7 | 팀장 이상 |
| `permitAll()` | 3 | 공개 경로에도 의도를 명시 (Semgrep `AUTHZ_001` 대응) |

### 레이트 리밋

한 사무실에서 직원 수십 명이 IP 하나를 공유한다. 그래서 **경로마다 값이 다르다** —
전부 같은 숫자로 두면 공격자에게 넉넉하거나 사무실을 막거나 둘 중 하나가 된다.

| 경로 | 기준 | 한도 | 왜 이 값인가 |
|---|---|---|---|
| 로그인 | IP | 60회/분 | 아침 출근 시간에 몰린다. 진짜 방어는 아래 계정 기준이 한다 |
| 로그인 | **계정 (실패만)** | 5회/5분 | 무차별 대입 방어의 본체. 잘 쓰는 사용자는 닿지 않는다 |
| 토큰 재발급 | IP | 120회/분 | 액세스 토큰(30분) 만료마다 사람 수만큼 나간다 |
| 기업코드 조회 | IP | 20회/분 | `CompanyCodeGenerator` 의 전수 탐색 난이도 계산이 이 값을 전제한다 |
| 회사 등록 | IP | 5회/분 | 성공하면 회사·오너 계정이 생기고 되돌릴 경로가 없다 |
| 비밀번호 변경 | 구성원 (실패만) | 5회/5분 | 토큰 하나를 훔친 사람이 현재 비밀번호를 반복 시도하는 통로 |
| 비밀번호 찾기 | IP | 5회/분 | 공개 경로 = 익명 대량 요청 표면 |
| 비밀번호 찾기 | **계정 (성공도 카운트)** | 3회/24h | 여기만 성공도 센다 — 성공하는 순간 비밀번호가 실제로 바뀌어 로그인이 막히므로, **성공 자체가 공격 수단**이다 |

Redis 고정 윈도우 방식이며, Redis 장애 시 **fail-open** 이다 (인증 자체를 막지 않는다).

> ⚠️ 이 값들이 뜻을 가지려면 `server.forward-headers-strategy` 가 켜져 있어야 하고,
> **가장자리에서 클라이언트가 보낸 `X-Forwarded-For` 를 덧붙이는 게 아니라 걷어내야** 한다.
> 덧붙이기만 하면 위조값이 맨 앞에 남아 IP 기준 제한이 통째로 뚫린다.

### 세션·토큰

| 항목 | 값 |
|---|---|
| Access TTL | 30분 |
| Refresh TTL | 1일 (`keepSignedIn = false`) / 14일 (`true`) |
| **Refresh 절대 상한** | **30일** |
| 회전 | 재발급 시 이전 토큰 폐기 · 재사용 탐지 시 세션 전체 폐기 |

절대 상한이 없으면 14일마다 한 번 갱신하는 것만으로 세션이 영원히 산다 —
**탈취된 갱신표가 영구 세션이 된다**는 뜻이다.

### 감사 로그

`AuthzAuditLogger` 가 인가 실패 · 로그인 실패 · **리프레시 토큰 재사용(탈취 정황)** 을
`AuthzOutcome` 으로 분류해 남긴다. 탈취 정황은 ERROR 레벨이다.

### 테넌트 격리

멀티테넌트 SaaS 라 모든 조회에 회사 경계가 들어가야 한다. 이걸 **Semgrep 규칙이 CI 에서 강제**한다.

| 규칙 | 잡는 것 |
|---|---|
| `TENANT_001` | 리포지토리 메서드 시그니처에 `CompanyId` 가 없는 조회 |
| `AUTHZ_001` | `@PreAuthorize` 가 빠진 컨트롤러 메서드 |
| `QUERY_002` | 새 `@Query` 애노테이션 도입 |

### 비밀 설정

| 파일/경로 | 용도 |
|---|---|
| `application-secret.yml` (**프로젝트 루트**) | 로컬 개발용. `.gitignore` 처리 |
| `.env` (**프로젝트 루트**) | docker compose 용 Redis 비밀번호 |
| **AWS SSM Parameter Store** `/z/prod/` | 운영. SecureString. 환경변수로 주입 |

> ⚠️ 비밀 파일을 `src/main/resources` 아래에 두면 안 된다. `.gitignore` 와 무관하게
> `processResources` 가 jar 에 담고 `Dockerfile` 의 `COPY src` 가 이미지에 싣는다.
> `build.gradle` 이 `processResources` 에서 `application-secret*` 을 제외해 부분 방어를 하지만,
> **`COPY src` 는 막지 못한다** — 규약대로 루트에 두는 것이 유일한 해법이다.

### fail-closed 설계 예시

`infra/docker-compose.yml` 은 `SPRING_PROFILES_ACTIVE: prod` 를 **파일에 직접 박는다.**
SSM 파라미터에 맡기면, 값이 없을 때 프로파일이 통째로 비고 `@Profile("!prod")` 빈들이
운영에서 뜬다 — 그중 `LoggingAccountMailAdapter` 는 발급 비밀번호를 평문 로그에 찍는다.
**설정을 빠뜨린 것이 유출이 되는 구조**였다.

---

## AI 코드 리뷰 루프

이 저장소에는 자체 제작한 **LLM 코드 리뷰 파이프라인**(`reviewloop` 도메인, 58개 파일)이 들어 있다.
`git push` 하면 두 개의 게이트가 돈다.

```mermaid
flowchart LR
    P[git push] --> G1{"Gate 1<br/>ArchUnit · Semgrep · ShellCheck"}
    G1 -->|실패| BLOCK["push 차단"]
    G1 -->|통과| G2["Gate 2<br/>LLM 판정 · Gemini"]
    G2 --> REP["수정 요청서<br/>차단하지 않음"]
    REP --> CC["Claude Code 가 수정"]
    CC --> V["review-verify.sh<br/>javac + test"]
    V --> P2["사람 승인 → 커밋"]
```

### 이 루프의 불변식

> **찾는 주체와 고치는 주체는 절대 같지 않다.**

| 역할 | 주체 | 성격 |
|---|---|---|
| **찾기(판정)** | Gemini (`GeminiJudgeAdapter`) | LLM · 배치 |
| **채점** | 결정론 코드 (`JudgeScorer`) | LLM 아님 · `rules.yaml` 이 SSOT |
| **고치기** | **Claude Code** (사람이 운전) | Edit 도구 · 사람 승인 |
| **검증** | 결정론 코드 (`javac` + `test`) | LLM 아님 |

이전 버전은 Gemini 가 찾고 Gemini 가 고쳤다 — **자기 승인**이었다. 그 경로(`reviewAutoFix`)는
휴면 상태로 내려두고, 판정자와 수정자를 교차시킨 드라이버 루프를 기본 경로로 삼는다.

### 규칙 카탈로그

`review-loop/rules.yaml` 이 규칙·가중치·임계값의 단일 진실 소스다.

| ID | 심각도 | 내용 | 집행 |
|---|---|---|---|
| `ARCH_001~003` | MINOR | 레이어 의존 방향 | ArchUnit (Gate 1) |
| `TENANT_001` | MINOR | 회사 경계 없는 조회 | Semgrep (Gate 1) |
| `AUTHZ_001` | MINOR | `@PreAuthorize` 누락 | Semgrep (Gate 1) |
| `QUERY_002` | MINOR | 새 `@Query` 도입 | Semgrep (Gate 1) |
| `MIG_001` | **CRITICAL** | 적용된 마이그레이션 수정 | Gate 2 |
| `CONV_001` `PERF_001` `NOTE_001` | MINOR | 컨벤션 · 성능 · 주석 | Gate 2 |

### Gradle 태스크

```bash
./gradlew installReviewHooks
```

```bash
./gradlew reviewLoop --args="--path src/main/java"
```

```bash
./gradlew reviewAccuracy
```

```bash
bash scripts/review-verify.sh --with-test
```

우회가 필요하면 `git push --no-verify`.

| 문서 | 내용 |
|---|---|
| [review-loop/SETUP.md](review-loop/SETUP.md) | 팀원 세팅 — 클론 후 1회 |
| [review-loop/DRIVER.md](review-loop/DRIVER.md) | 절차 — push 하면 무슨 일이 일어나고 무엇을 해야 하나 |
| [review-loop/UNIFIED_DESIGN.md](review-loop/UNIFIED_DESIGN.md) | 설계·결정 기록 |
| [review-loop/rules.yaml](review-loop/rules.yaml) | 규칙 카탈로그 (SSOT) |

---

## CI/CD

### 워크플로

| 워크플로 | 트리거 | 잡 |
|---|---|---|
| **Backend CI** | PR → `develop`/`main`, push → `develop` | `test` (Gradle) · `migration-check` (실 MySQL) · `docker` (빌드) · `trivy-config` (IaC 스캔) · `dependency-submission` |
| **Gate 1 · Semgrep** | PR | `QUERY_002` · `TENANT_001` · `AUTHZ_001` |
| **Gate 1 · ShellCheck** | PR | 셸 스크립트 정적 분석 |
| **Gate 2 · LLM Judge** | PR | LLM 판정 리포트 |
| **CD (Spring Deploy)** | push → `main` | Docker Hub 푸시 → **AWS OIDC** 인증 → SSM 으로 EC2 배포 |

배포에 **액세스 키를 쓰지 않는다** — GitHub OIDC 로 IAM 역할을 assume 한다.
액션은 전부 **커밋 SHA 로 핀** 되어 있고, 체크아웃은 `persist-credentials: false` 다.

### 취약점 대응

`build.gradle` 상단에서 Spring Boot BOM 이 못 박은 전이 의존성 버전을 **위로 덮어쓴다.**

| 라이브러리 | 이유 | 끌고 오는 쪽 |
|---|---|---|
| `netty` 4.2.16 | Bzip2Decoder 무한 루프 → 이벤트 루프 정지 (GHSA-558v-64gr-wgg4) | AWS SDK netty-nio-client |
| `httpcore5` 5.4.3 | HTTP/1 헤더 파싱 메모리 고갈 DoS (GHSA-hf6x-8p5f-cgmf) | anthropic-java |
| `jackson` 2.21.5 / 3.1.5 | `@JsonView`·`@JsonIgnoreProperties` 우회 4건 | BOM |

> ⚠️ Spring Boot 를 올릴 때 이 블록을 함께 봐야 한다. BOM 이 더 높은 버전을 물어오게 되면
> 그 줄은 **지워야** 한다 — 남겨두면 BOM 보다 낮은 버전에 묶는 반대 효과가 난다.

### 모니터링

`infra/monitoring/` 에 Prometheus + Grafana 프로비저닝이 통째로 들어 있다.

- 대시보드 2종 (메트릭 · 로그)
- 알림 규칙 + 컨택트 포인트
- `redis_exporter`

---

## 로컬에서 실행하기

### 필요한 것

- JDK 17
- MySQL 8
- Docker Desktop (로컬 Redis 용)

### 1. MySQL 계정·DB 생성

```sql
CREATE USER IF NOT EXISTS 'module06'@'localhost' IDENTIFIED BY '본인이_정할_비밀번호';
ALTER USER 'module06'@'localhost' IDENTIFIED BY '본인이_정할_비밀번호';
CREATE DATABASE IF NOT EXISTS module06 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON module06.* TO 'module06'@'localhost';
FLUSH PRIVILEGES;
```

테이블은 만들지 않는다 — Flyway 가 부팅 시 적용한다.

### 2. 설정 파일 두 개

```bash
cp .env.example .env
```

```bash
cp application-secret.yml.example application-secret.yml
```

Redis 비밀번호를 하나 만들어 **두 파일에 같은 값**을 넣는다.

```bash
openssl rand -hex 32
```

| 파일 | 읽는 주체 | 역할 |
|---|---|---|
| `.env` | docker compose | 이 값으로 Redis 를 **띄운다** (`--requirepass`) |
| `application-secret.yml` | Spring | 이 값으로 Redis 에 **접속한다** (AUTH) |

둘 다 **프로젝트 루트**에 둔다. 한쪽만 채우면 `NOAUTH` 또는 `WRONGPASS` 로 갈린다.

### 3. Redis 띄우기

```bash
docker compose up -d
```

### 4. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 5. 확인

```bash
curl http://localhost:8080/actuator/health
```

Swagger UI → <http://localhost:8080/swagger-ui.html>

### 6. push 게이트 활성화 (권장)

```bash
./gradlew installReviewHooks
```

> 막히는 곳이 있으면 **[docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)** 의 증상별 원인 표를 보면 된다.
> 순서 중 하나라도 빠지면 원인을 가리키지 않는 에러가 난다.

---

## 테스트

```bash
./gradlew test
```

```bash
./gradlew migrationCheck
```

| 유형 | 개수 |
|---|---:|
| `@SpringBootTest` | 69 |
| `@WebMvcTest` | 21 |
| 전체 테스트 파일 | 345 (63,776 라인) |

### 테스트 JVM 힙을 2g 로 올린 이유

Gradle 기본값은 512m 인데, 설정이 서로 다른 스프링 테스트 컨텍스트가
`@SpringBootTest` 69개 + `@WebMvcTest` 21개만큼 캐시돼 512m 를 넘긴다.
CI·로컬 양쪽에서 `OutOfMemoryError` 가 났고, 터진 자리가 Jackson 빈이라 Jackson 문제로
보였지만 그건 하필 그 순간 메모리를 요청한 지점일 뿐이었다.

---

## 문서 지도

| 문서 | 내용 |
|---|---|
| [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) | 로컬 세팅 — MySQL · secret · Redis · 부팅 확인 (증상별 원인 표 포함) |
| [docs/API_SPEC.md](docs/API_SPEC.md) | 인증·인가·조직·구성원 API 명세 |
| [docs/AUTH_AUTHZ_SPEC.md](docs/AUTH_AUTHZ_SPEC.md) | 인증·인가 락 문서 (권한 모델의 정본) |
| [docs/DB_MIGRATION_RULES.md](docs/DB_MIGRATION_RULES.md) | Flyway 규칙 · 담당자별 버전 레인 |
| [docs/SECURITY_AUDIT_HANDOVER.md](docs/SECURITY_AUDIT_HANDOVER.md) | 보안 점검 결과 · 조치 목록 |
| [docs/토큰사용량-미터링-설계.md](docs/토큰사용량-미터링-설계.md) | 토큰 기반 과금 설계 |
| [docs/E-퇴사버튼-자동인수인계패키지.md](docs/E-퇴사버튼-자동인수인계패키지.md) | 인수인계 자동 조립 설계 |
| [docs/그라파나-모니터링-대시보드-설계.md](docs/그라파나-모니터링-대시보드-설계.md) | 관측 설계 |
| [infra/monitoring/README.md](infra/monitoring/README.md) | Prometheus · Grafana 운영 |
| [review-loop/](review-loop/) | AI 코드 리뷰 루프 (설계 · 절차 · 규칙) |

> ⚠️ `docs/` 의 설계 문서 중 상당수는 **설계 시점 스냅샷**이다. 예외 정책·상태 enum·응답 필드
> 같은 실제 계약은 그 뒤 코드에서 확정됐다. **정본은 항상 `develop` 의 코드**이며,
> 문서와 코드가 다르면 코드가 옳다.

---

## 개발 규약

### 브랜치

```
main ← develop ← feat/… · fix/… · refactor/…
```

`main`·`develop` 모두 브랜치 보호가 걸려 있고, Gate 1 체크가 필수다.

### 커밋 메시지

```
[FEAT] 비밀번호 찾기 — 잃어버린 비밀번호의 복구 경로를 처음으로 만든다
[FIX]  비대면 회의 상세 500 — 회의실 없는 회의(meetingRoomId=null) NPE 방어
[TEST] 비밀번호 찾기 회귀 방지 — permitAll 누락과 AU 코드 충돌을 테스트가 잡게 한다
```

`[TYPE] 무엇을 — 왜 / 무엇이 달라지는가` 형태. 타입은
`FEAT` `FIX` `HOTFIX` `REFACTOR` `TEST` `DOCS` `PERF` `CHORE`.

### PR

[PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) 를 따른다.
연관 이슈 · 작업 내용 · **프론트엔드 연동 가이드(엔드포인트 · 요청 파라미터 · 응답 예시 · 주의사항)** 를 채운다.

### 코드에 "왜"를 남긴다

이 저장소의 주석은 *무엇을 하는지*가 아니라 **왜 그렇게 했는지, 안 그러면 무슨 일이 나는지**를
적는다. 위 문서 곳곳에 인용된 설명들이 전부 코드 주석에서 온 것이다.

```java
/*
 * 알 수 없는 값은 예외로 드러낸다. null 이나 기본값으로 넘기면 계층 하나가 조용히
 * 사라진 채 "분석 완료"로 보이는데, 그건 이 파이프라인에서 가장 위험한 실패 방향이다.
 */
```

---

## 기여자

`develop` 기준 1,146 커밋 · 6명.

| | 커밋 |
|---|---:|
| [@mosungjin](https://github.com/mosungjin) | 383 |
| [@mnppi223](https://github.com/mnppi223) | 219 |
| [@Yoonjongho1122](https://github.com/Yoonjongho1122) | 160 |
| [@dlxodus02](https://github.com/dlxodus02) | 155 |
| [@hyunj11](https://github.com/hyunj11) | 123 |
| [@jongjunn](https://github.com/jongjunn) | 106 |

<div align="center">
<sub><b>Z</b> · module06-4 · 2026</sub>
</div>
