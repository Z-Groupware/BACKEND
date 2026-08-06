# E 모듈 — "레거시 컴파일러" 극대화 패키지 (Codex 구현 브리프)

> ⏳ **설계 시점 스냅샷 (2026-08-06 보존)** — 이 문서는 E 모듈 설계·기획 과정의 기록이며 최종 구현이 아니다. 예외 정책·상태 enum·응답 필드·경계식 등 실제 계약은 그 뒤 `develop` 코드에서 확정됐다. **정본은 항상 `develop`의 코드**이며, 문서와 코드가 다르면 코드가 옳다.

> 작성 2026-08-03 · 담당 박종준(PO) · 브랜치 `claude/offboarding-handover-progress-c5f4a0`
> 성격: 6블록(사실 조립) 위에 얹는 **파생 인텔리전스 레이어**. 새 데이터 캡처·무거운 AI 신규호출 없이 **기존 회의 그래프(Meeting↔Action↔User↔Time)를 계산해 후임/관리자에게 이식**.

---

## 0. 이 브리프의 원칙 (반드시 지킬 것)

1. **정직성 게이트가 스펙의 일부다.** 데이터가 없으면 기능도 없다. 아래 "정직 제약"을 위반하는 표현/수치는 구현 금지.
2. **아키텍처 = 스냅샷 + BE 조립.** 표시값은 **최종승인(finalize) 시점에 포트로 1회 조회**해 스냅샷 테이블에 찍어 영구 보존. 상세/PDF는 스냅샷만 읽어 실시간 크로스조회 0.
3. **경계 준수.** handover 패키지 밖 테이블에 write 금지. 타 모듈 데이터는 **읽기 포트(out port)로만** 접근. 회의 생성·캘린더 write·권한 변경은 E 소관 아님.
4. **컨벤션.** `com.module06.backend`, 순수 도메인 모델 ↔ `XxxJpaEntity` 분리, `UseCase/Service/Command/Port/PersistenceAdapter` 접미사. (기존 handover 도메인과 동일)

---

## 1. 채택 기능 4종

### ① 오너십 지도 (Ownership Map) — 관리자용
- **무엇:** 퇴사자가 **사실상 책임지던** 프로젝트/토픽을, 문서가 아니라 회의 참여로 복원.
- **계산:** 퇴사자의 소속·참여 프로젝트별로
  - 주최 횟수 = `meeting.host_member_id = 퇴사자` count
  - 참석 횟수 = `meeting_attendee` count
  - 담당 액션 수 = `action.assignee_member_id = 퇴사자` count (PERSONAL)
  - → 프로젝트별 가중 점수로 정렬, 상위 N개 = "이 사람의 핵심 영역".
- **정직:** 휴리스틱. UI 문구 "추정 핵심 영역", **점수/퍼센트 노출 금지**(내부 정렬용으로만).

### ② 고아 업무 경보 (Orphaned Team-Work Alert) — 관리자용
- **무엇:** 우리 규칙상 **재분배는 PERSONAL만** → 퇴사자가 사실상 끌던 **TEAM 액션**은 대상이 아니라 조용히 주인을 잃는다. 이 사각지대를 경보.
- **계산:** 퇴사자 참여 진행 프로젝트의 `action(action_type=TEAM, status != DONE)` 중, 그 액션의 `source_meeting`을 퇴사자가 **주최했거나 참석 지배적**인 건 → 후보. 재분배 세트에 포함 안 되는 것만.
- **UI:** "재분배 대상은 아니지만 사실상 퇴사자가 주도하던 팀 업무 — 후임 지정 **권고**." 
- **정직:** 휴리스틱·"권고"만. 자동 재분배 금지(TEAM은 담당자 개념 없음), 확률/수치 금지.

### ③ 질문 라우팅 · Ask-Whom (지식 계보 라이트) — 후임자용
- **무엇:** 인계 액션별 "막히면 누구한테 물어봐".
- **계산 (액션 단위):**
  - **Originator(배경):** 그 액션이 속한 프로젝트의 **최초 회의**(min `start_at`) 참석자 = 배경 맥락 보유자.
  - **Executor(최근 실무):** 퇴사자가 참여한 **최근 회의의 공동 참석자**(`meeting_attendee` via MeetingQueryPort) = 최근 실무 파트너. *(주: "공동 담당자(co-assignee)"가 더 강한 신호지만, C의 타인 액션 노출=스코프 확장이라 회피. 회의 공동참석을 정직·저비용 프록시로 채택.)*
  - 각 1~2명, `OrgQueryPort.findMembers`로 name/position 조인.
- **UI:** "막히면 → 배경은 {Originator}, 최근 실무는 {Executor}."
- **정직:** 순수 그래프. **Pivot Controller(변곡점 증인)는 구현 금지** — `action` 상태 audit·화자 태깅 데이터가 없어 못 만든다.

### ④ 액션별 회의 맥락 타임라인 (Context Timeline) — 후임자용 · **1순위 킬러**
- **무엇:** 인계받은 액션 카드에서 "이 업무가 어떤 흐름으로 왔는지" 회의 맥락을 시간순으로.
- **계산 (액션 단위):** action → `source_meeting_id` 및 같은 `project_id`의 회의들을 **`start_at` 오름차순**으로, 각 회의의 `meeting_topic`(MAIN/SUB content) 발췌를 타임라인으로 조립.
- **UI 예:** `3/2 킥오프: 보안 이슈로 B방식 채택` → `3/15 점검: 외주 지연으로 스펙 확정 연기` → "이전 담당자는 이 흐름으로 끌어왔습니다."
- **정직:** **기존 요약(meeting_topic) 재조립. 새 AI 호출 없음.** 화자 데이터가 없으므로 "퇴사자 발언만"이 아니라 **"이 업무의 회의 맥락"**으로 프레이밍.

---

## 2. 컷 / 보류 (구현 금지 — 지어낸 데이터)

| 원안 | 판정 | 이유 |
|---|---|---|
| Pivot Controller (변곡점 증인) | **컷** | `action` 상태변경 이력·회의 화자 태깅 둘 다 없음 |
| Rhythm 캘린더 **자동삽입** | **컷** | 회의/스케줄 생성은 D 소관, write·recurrence 인프라 없음, 조립철학 위반 |
| Rhythm **리포트**(탐지만) | 선택 | `start_at` 주기성 탐지까지는 정직 → 원하면 "정기 회의 패턴" 표시 + D 회의개설 프리필로 위임 |
| Blast Radius **확률/의존도 수치** | **컷** | 액션 dependency 데이터 없음. "블락확률 90%"는 조작 |
| Blast Radius **연관 표시** | 선택 | "같은 회의에서 파생된 관련 업무" 나열까지만(인과·수치 주장 없이) |

---

## 3. 정직 제약 3 (UI/PDF 공통)
1. 화자 데이터 없음 → "퇴사자 발언만" 류 표현 금지 → "회의 맥락".
2. `action` status audit 없음 → 변곡점/상태이력 기반 주장 금지.
3. ①②는 휴리스틱 → **가짜 %/확률/점수 노출 금지**, "추정/권고"만.

---

## 4. 스키마 증분 (V2.1.x 레인 · 스냅샷)

신설 스냅샷 테이블 1개 제안 (최소화):

```
handover_insight
  id           BIGINT PK AUTO_INCREMENT
  handover_id  BIGINT NOT NULL        -- 소속 인수인계
  action_id    BIGINT NULL            -- NULL=handover레벨(①②) / 값=액션레벨(③④)
  kind         ENUM('OWNERSHIP','ORPHAN_ALERT','ASK_WHOM','CONTEXT_TIMELINE') NOT NULL
  payload      JSON NOT NULL          -- 조립 결과 스냅샷
  sort_order   INT NOT NULL DEFAULT 0
  created_at / updated_at
```

- **⚠️ Codex 확인 필수:** MySQL `JSON` 컬럼이 팀 Flyway **5대 금지사항**(`docs`/CLAUDE.md의 마이그레이션 규칙)에 걸리는지 검증. 걸리면 → 정규화 자식행(`handover_insight_item`) 또는 `TEXT`로 대체.
- 채우는 시점 = **finalize(@Transactional) 안에서** 포트 조회 후 insert. handover 삭제(soft) 규칙과 한 트랜잭션.

---

## 5. 크로스모듈 의존 (협의 대상 — 단독 구현 금지)

**✅ 협의 타결 (2026-08-03) — 3건 모두 확정, 목 없이 실구현 가능**

| 포트 | 소유 | 확정 내용 |
|---|---|---|
| `MeetingQueryPort` (신설) | D / 모성진 | **E 재량 위임.** `findProjectMeetingsOrdered(projectId)`(start_at asc) · `findMeetingTopics(ids[])` · `findMeetingAttendees(ids[])`. 라이브 조회(회의 불변), **soft delete 회의/프로젝트 제외**, 배치 조회 |
| `ActionReassignPort` (기존) | C / 김민섭 | ①`findHandoverableActions` 반환에 `sourceMeetingId`+`description` 추가(스키마 무변) ②TEAM 액션은 **C가 `findTeamActionsForDeparture(memberId)` 신설**(읽기전용, 재분배 대상 아님) — ②고아경보용. E가 action 직접 read 금지 |
| `OrgQueryPort` (기존) | B / 종호 | **E 재량 위임.** `findMembers(memberIds[])` → `{memberId, name, position}` 배치 |

**퇴사 범위 확정(PO):** `findHandoverableActions`·`findTeamActionsForDeparture` 모두 **퇴사자 본인 스코프(fromMemberId)만**. 프로젝트 내 타인 액션 불포함. OFFBOARDING은 상태무관 전체(완료 포함, 완료는 기록용).

**`findTeamActionsForDeparture` 반환 필요 필드:** `{actionId, title, projectId, sourceMeetingId, status, teamId}` (②고아경보가 회의 지배도 계산에 sourceMeetingId 사용).

---

## 6. 스코프 아웃 (이번 브리프 밖)
- 컨트롤러/REST 엔드포인트 (공용 B 스켈레톤 후)
- PDF 생성 (FE 담당)
- 알림 (스코프 아웃 유지)
- Rhythm 자동삽입 / Blast Radius 수치 (§2)

---

## 7. 완료 기준 (Acceptance)
1. `handover_insight` 마이그레이션 + JpaEntity/PersistenceAdapter (컨벤션 준수, Flyway 5금지 통과).
2. finalize 시 4종 kind 스냅샷 조립·저장 (크로스모듈 포트는 인터페이스+목 허용).
3. 도메인 순수성 유지(handover 밖 write 0, global.common import 0).
4. 정직 제약 3 위반 표현/수치 코드에 없음.
5. `gradle build` 성공 + 스냅샷 조립 단위테스트. (Codex 샌드박스 네트워크 차단 시 Claude가 대신 빌드 검증)

---

## 8. 구현·검증 로그 (2026-08-04)

**상태: 1차 구현 완료 + Claude 빌드검증 GREEN (전체 97 테스트 통과).**

Codex 산출: `V2.1.1__create_handover_insight.sql`(payload JSON, Flyway 5금지 통과) · domain(HandoverInsight, HandoverInsightKind) · application(FinalizeHandoverInsightsUseCase/Service/Command) · port/out 4 · infrastructure/persistence(JpaEntity/Repository/Adapter) · 서비스 단위테스트.

**Claude 검증 중 수정 3건 (Codex 샌드박스가 컴파일 못 해 남은 흠):**
1. `build.gradle` + `jackson-datatype-jsr310` 명시 추가 — CONTEXT_TIMELINE payload의 `LocalDateTime` 직렬화 의존성 누락(런타임 터짐).
2. 테스트 `provider()` 헬퍼 람다 → 익명클래스 — `ObjectProvider`는 함수형 인터페이스 아님.
3. **서비스가 존재하지 않는 `ObjectMapper` 빈을 주입 요구 → `contextLoads()` 붕괴.** 이 앱은 비표준 `spring-boot-starter-webmvc`라 ObjectMapper 오토컨피그가 안 됨. → 서비스가 **자체 보유 static ObjectMapper(JavaTimeModule)** 로 교정. 스냅샷 직렬화는 앱 web 설정과 무관해야 안정적이므로 설계상으로도 이 방향이 옳음.

**설계 특징:** 포트 3종은 `ObjectProvider<>` 로 lazy 주입 → C/D/B 구현체가 없어도 컨텍스트 정상 기동, finalize 호출 시 미구현이면 `"... not available yet"` throw.

**남은 TODO:**
- 베이스 handover 도메인(`Handover`·`HandoverItem`·finalize)이 이 브랜치에 부재 → insight finalize가 **실제 finalize 흐름에 아직 미배선**. 베이스 병합 시 호출부 연결 필요.
- C/D/B 포트 실구현 대기(현재 lazy provider가 throw).
- 실제 DB 부팅/통합 검증은 MySQL 환경에서.
