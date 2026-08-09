# E · 인수인계·휴직 — 계약 · ERD (W0 산출 · 확정본)

> ⏳ **설계 시점 스냅샷 (2026-08-06 보존)** — 이 문서는 E 모듈 설계·기획 과정의 기록이며 최종 구현이 아니다. 예외 정책·상태 enum·응답 필드·경계식 등 실제 계약은 그 뒤 `develop` 코드에서 확정됐다. **정본은 항상 `develop`의 코드**이며, 문서와 코드가 다르면 코드가 옳다.

> 담당 박종준(PO) · 작성 2026-08-03(W1 첫날) · 상태 **확정** (2026-08-03 결정 반영)
> 근거: `잇다(Z) 백엔드 도메인 설계.md` §5.9/5.10, 프론트 워크플로우 스펙 v2, docs/PLAN.md, 컨벤션 §1.1

---

## 0. 스코프 확정 (2026-08-03 결정)

| 항목 | 결정 | 영향 |
| --- | --- | --- |
| 오프보딩 계정 처리 | **Soft delete, 오너·어드민 최종승인 시** — `MemberStatus.DELETED`로 남김 + 권한·자원 회수 '잔여 0' 감사. 하드삭제는 추후 | 물리삭제 안 함(데이터 보존). 회수 로직은 B 도메인 소유, HO가 최종승인에서 호출 |
| 인수인계서 PDF | **BE 미생성.** FE가 BE의 액션 근거 데이터를 받아 PDF 생성·발행 | 자바 PDF 라이브러리 스코프 아웃. BE는 상세 응답 스키마만 책임 |
| 인수인계 알림 | **현재 스코프 아웃.** 알림은 회의생성·회의10분전만 구현(D/알림 도메인). 인수인계 상신·승인·반려 알림은 추후 추가 가능 | 협의 대상에서 알림 제외 → 실질 3건 |
| F 스토리지 의존 | **없음** | PDF가 FE로 이관되며 파일 저장 의존 제거 |

---

## 1. 크로스도메인 계약 (경계를 넘는 "값")

**아키텍처 결정(2026-08-03)**: **스냅샷 + BE 조립.** E는 남의 도메인 데이터를 id로만 저장하되,
표시값(액션 제목·상태, 사람 이름·직급)은 **생성/재분배 시점에 포트로 1회 조회해 handover에 찍어 저장**한다.
→ 상세/PDF 조회 시 크로스도메인 실시간 조회 0, 인수인계 시점 상태가 영구 보존(액션·사람이 나중에 바뀌어도 불변).

E가 **정의**하고 이웃이 **구현**하는 포트(전부 `handover/application/port/out`, 공용 아님):

| # | 방향 | 상대 | 포트 | 시그니처 · 반환 필드 = E가 받는 값 |
| --- | --- | --- | --- | --- |
| 1 | READ+WRITE | **C 액션 (김민섭)** | `ActionReassignPort` | `findHandoverableActions(memberId, type)` → **[actionId, title, projectTag, actionType, status, deadline, sourceMeetingId, sourceMeetingTitle, content]** · 범위: **LEAVE=미완료만 / OFFBOARDING=진행중 프로젝트 전체(완료 포함)** · `reassign(actionId, from, to)` (개별 액션, PERSONAL만) |
| 4 | READ | **D 회의 (모성진)** | `MeetingQueryPort` (신설) | `findMeeting(meetingId)` → **{date, attendees[], decisionSummary, actionItemsSummary}** — 퇴사패키지 §5 회의 히스토리용. 회의=불변 이력이라 라이브 조회 허용(스냅샷 예외) |
| 2 | READ | **B 회원·조직 (윤종호)** | `OrgQueryPort` (신설) | `findTeamLeaderId(teamId)` → 상신 대상 · `findMember(memberId)` → **{memberId, name, position}** (작성자 스냅샷) · `findReassignCandidates(teamId, excludeMemberId)` → **[{memberId, name, position, actionCount}]** (DnD 후보=**같은 팀**, 작성자 제외, 각자 기존 액션 수 포함) |
| 3 | WRITE | **B 회원 (윤종호)** | `MemberStatusPort` | `toWaiting` / `toVacation` / `offboard` / `restoreActive` (intent 기반, 확정됨) |
| — | 응답 | **FE (이홍근)** | 상세 응답 스키마 | 스냅샷 필드 그대로 내려줌 → FE가 PDF 발행 |
| — | 보류 | ~~알림~~ | ~~`NotificationPort`~~ | 인수인계 알림 추후. 현재 알림=회의생성·10분전만 |

---

## 2. ERD

> 변수명은 컨벤션표 기준: `handoverType`, `leaveStartAt/leaveEndAt`, `reassigneeId`. `*Snap`=스냅샷 컬럼.

### 2.1 `handover` (인수인계서)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `writer_member_id` | BIGINT | 작성자 id만(참조) |
| `team_id` | BIGINT | 작성자 팀(팀장 라우팅용) id만 |
| `handover_type` | ENUM | `VACATION` / `OFFBOARDING` |
| `status` | ENUM | `SUBMITTED` / `REASSIGNED` / `FINALIZED` |
| `leave_start_at` / `leave_end_at` | DATETIME null | VACATION만 |
| `last_working_day` | DATE null | OFFBOARDING 최종근무일 (퇴사패키지 §1) |
| `writer_name_snap` | VARCHAR | 작성자 이름 스냅샷(생성 시) |
| `writer_position_snap` | VARCHAR | 작성자 직급 스냅샷 |
| `intermediate_approver_id` | BIGINT null | 중간승인한 팀장 id (참조) |
| `intermediate_approver_name_snap` | VARCHAR null | 중간승인 팀장 이름 스냅샷 |
| `intermediate_approved_at` | DATETIME null | 팀장 중간승인(complete) 시각 |
| `reject_reason` | VARCHAR null | 반려 사유(반려 시 필수) |
| `created_at` / `updated_at` | DATETIME | |
| `finalized_at` | DATETIME null | 최종 승인 시각 |

### 2.2 `handover_item` (인수인계 액션 항목)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | BIGINT PK | |
| `handover_id` | BIGINT FK→handover | 같은 도메인 강결합 `@ManyToOne` |
| `action_id` | BIGINT | 인계 대상 액션 id만(참조) |
| `action_title_snap` | VARCHAR | 액션 제목 스냅샷(생성 시) |
| `action_status_snap` | ENUM | 액션 상태 스냅샷 |
| `project_tag_snap` | VARCHAR null | 프로젝트 태그 스냅샷 |
| `action_type_snap` | ENUM | TEAM/PERSONAL 스냅샷 |
| `deadline_snap` | DATE null | 마감 스냅샷 (퇴사패키지 §2 마감임박·§8 파생용) |
| `source_meeting_id` | BIGINT null | 출처 회의 id (§5 회의 히스토리 연결) |
| `source_meeting_title_snap` | VARCHAR null | 출처 회의명 스냅샷 (§3 표시) |
| `reassignee_id` | BIGINT null | 재분배 대상 member id. 미분배=null |
| `reassignee_name_snap` | VARCHAR null | 재분배 대상 이름 스냅샷(재분배 시) |
| `reassignee_position_snap` | VARCHAR null | 재분배 대상 직급 스냅샷 |
| `reassigned_at` | DATETIME null | |

> **휴직(leave)은 별도 테이블 없음.** 휴직 신청 = `handover(handover_type=VACATION)`. 휴직 상태는 handover.status + member.status로 표현. `LV` 접두어는 휴직 검증(날짜·중복·팀장특수)에만.
>
> **확정(2026-08-03, FE 화면 대조)**: 재분배 단위=**개별 액션**(스크린2 개별 DnD) / 오프보딩 범위=**미완료만** / 재분배 후보=**같은 팀원**(작성자 제외).
>
> **⚠️ C(김민섭) 남은 확인 1건**: 액션 상태 enum 실제값 — 스크린1엔 "리뷰"가 있는데 스크린3(대기/진행중/완료=TODO/IN_PROGRESS/DONE)과 불일치. 스냅샷 저장값이라 확정 필요.

---

## 3. 상태 전이

### 3.1 Handover
```
(생성) → SUBMITTED ─[팀장 완료/중간승인]→ REASSIGNED ─[오너·어드민 최종승인]→ FINALIZED
                └──────────────[반려]──────────────┘  (어느 단계서든 반려 가능)
```
- SUBMITTED→REASSIGNED 전제: **모든 handover_item 재분배 완료**(reassigned_member_id 전부 채워짐).
- REASSIGNED→FINALIZED 시 실제 액션 소유자 커밋 + 멤버 상태 확정.

### 3.2 Member (작성자)
```
ACTIVE ─[신청/생성 즉시]→ WAITING ─[VACATION 최종승인]→ VACATION
                            │        └[OFFBOARDING 최종승인]→ DELETED (soft, 회수+감사)
                            └─────────[반려]─────────→ ACTIVE (원복)
```
- 임시저장 없음: 생성 즉시 SUBMITTED + 작성자 WAITING.

---

## 4. 엔드포인트 (초안 · 8/9 계약 프리즈 대상)

### 4.1 신청자 (MEMBER↑, ADMIN 제외 / LEADER는 OFFBOARDING 토글 없음)
| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/handovers` | 인수인계서 생성(=휴직/오프보딩 신청). body: `type`, `vacationStart/End`(VACATION), `actionIds`(VACATION 선택 / OFFBOARDING 전체 자동) |
| GET | `/api/handovers/me` | 내 인수인계서 조회 |
| GET | `/api/handovers/{id}` | 상세(타임라인·재분배·승인이력 = **PDF용 전 필드**) |

### 4.2 팀장 (LEADER · 재분배 · 중간승인)
| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/team/handovers` | 팀 상신 목록 |
| GET | `/api/team/handovers/{id}` | 재분배 보드 상세 |
| PATCH | `/api/team/handovers/{id}/items/{itemId}/reassign` | DnD 재분배. body: `reassignedMemberId` |
| POST | `/api/team/handovers/{id}/complete` | **인수인계 완료=중간승인.** 전 항목 재분배 시만 활성. → REASSIGNED + 액션 재분배 커밋 |

### 4.3 오너·어드민 (최종승인)
| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/members/{memberId}/handover-summary` | 사원관리 상세(B 화면) 삽입용 **요약** — 유형·기간·인계건수·중간승인 표시(팀장 이름·시각). 타임라인 없음 |
| POST | `/api/handovers/{id}/finalize` | 최종 승인. VACATION→member VACATION / OFFBOARDING→member DELETED(회수+감사). → FINALIZED |
| POST | `/api/handovers/{id}/reject` | 반려. body: **`reason`(필수)** → `reject_reason` 저장 + 작성자 상태 ACTIVE 원복 |

> 팀장급 휴직 특수: 오너·어드민이 팀장 인수인계서를 사원관리 상세에서 직접 조회+`finalize`. 별도 엔드포인트 없이 권한 매트릭스로 처리.

---

## 5. 문서 충돌 해소 (확정)

**오프보딩 계정 삭제 시점** — 도메인 §5.9("팀장 완료 시 삭제") vs 프론트 스펙("최종승인 시 삭제")이 상충했으나
→ **오너·어드민 최종승인 단계에서 삭제로 확정**(2026-08-03). 도메인 §5.9 문구는 축약 오류로 정정.
팀장 완료(complete)는 **REASSIGNED까지만**, 계정 삭제는 최종승인(finalize)에서.

---

## 5.5 역할·유형별 인수인계 규칙 (2026-08-03 확정)

- **액션 범위**:
  - 휴직(LEAVE) = 퇴사자의 **미완료 액션만**.
  - 퇴사(OFFBOARDING) = 퇴사자가 참여한 **진행 중인 프로젝트** 안의 액션 **전부(완료 포함)**. 완료된 프로젝트 액션은 제외.
  - 완료 액션은 **기록용** — 재분배 불필요. → `complete()` 게이트는 **미완료 항목만 재분배** 요구.
  - 구현: `HandoverItem`에 `reassignRequired` 플래그(완료=false), `isAllReassigned()`는 reassignRequired만 검사. (C는 OFFBOARDING 시 진행중 프로젝트 필터 + 완료 포함해서 반환)
- **팀장 휴직**: 팀장 **본인**이 자기 개인 액션 후임 지정 → 오너·어드민 최종승인.
- **팀장 퇴사**: 오너가 그 팀장 액션을 **다른 팀장에게 수동 인계**(자동배정 아님).
- **새 팀장 지정**: **오너**가 지정 — 조직/auth 도메인(종호), E 아님.
- **host 회의**: **퇴사 시에만 삭제**(이관 아님, 휴직은 복귀하니 유지) — D 회의 도메인 처리. E는 관여 안 함.
- **일반 사원**: 팀장이 팀원 중 재분배(기존 흐름).

---

## 6. 다음 스텝

**블로킹 주의:** 엔티티·서비스는 `global.common`(ApiResponse·ErrorCode·BaseEntity)과 `member` 엔티티에 의존.
이건 **B(윤종호) id 계약 안정화(PLAN 최우선)** 선행 필요. → 충돌 없는 것부터 순서대로:

1. **[지금·E 단독]** 크로스도메인 **Port 인터페이스**(ActionReassignPort·MemberStatusPort) + **Enum**(HandoverType·HandoverStatus) 코드화 → C·B·FE에 시그니처 전달
2. **[B 스켈레톤 후]** `handover` 도메인 엔티티(Handover·HandoverItem) + Repository + PersistenceAdapter
3. 신청→상신 서비스 (member WAITING 전환 훅 포함), 상태전이
4. 재분배·중간승인 → 최종승인·반려 → 오프보딩 soft delete(최종승인)
5. 상세조회 응답(FE PDF용 전 필드)
