# Codex 구현 브리프 4 — 팀장 오프보딩 "귀속 대기 → 신규 팀장 일괄 이관"

> 대상: `com.module06.backend.handover` 패키지 **한정**. 계약 근거=`docs/E-인수인계휴직-계약ERD초안.md`, `docs/E-codex-brief.md`.
> 배경: FE(이홍근, `/owner/leader-handovers`) 요청 — 팀장 퇴사 인수인계서가 finalize 이후 **새 팀장 지정 전까지 "귀속 대기"** 로 남고, 새 팀장이 정해지면 그 인수인계서의 액션 **전부를 한 번에** 새 팀장에게 이관하는 API 2종이 없음.
> 스택 Spring Boot 3 / JPA / Java 17 / Lombok. 클린아키텍처+DDD, 헥사고날. 기존 handover 컨벤션 그대로.

---

## 0. 문제 정의 (왜 지금 안 되는가)

현재 상태머신은 오프보딩을 이렇게만 처리한다:

```
SUBMITTED --(항목 전부 재분배)--> complete --> REASSIGNED --(finalize)--> FINALIZED
```

- `complete()`는 `isAllReassigned()` (필수 항목 전부 reassignee 세팅)를 통과해야만 REASSIGNED로 간다.
- `finalize()`는 `status == REASSIGNED`에서만 동작한다.

**팀장 오프보딩은 이 경로에 안 맞는다.** 팀장 본인이 나가는데 **후임 팀장이 아직 없어서** 재분배 대상이 없다. 그래서:
1. 재분배를 못 하니 complete 못 하고,
2. complete를 못 하니 finalize도 못 한다.

즉 팀장 퇴사를 승인(finalize)하는 것 자체가 막힌다. 필요한 건 **후임 없이도 오너가 퇴사를 승인 → 액션은 "귀속 대기" 풀에 남았다가 → 새 팀장이 오면 일괄 이관**하는 별도 경로다.

읽기용 인사이트(`orphanAlert`/`askWhom`)는 "담당자 없는 액션"을 보여만 줄 뿐, 실제로 **확정 이관하는 쓰기 API가 이 브리프의 산출물**이다.

---

## 1. 경계 — 반드시 지킬 것 (위반 시 실패)

- **오직 `handover` 패키지 안에서만** 파일 생성/수정. 타 도메인·루트 설정·`global.common`·`SecurityConfig` **금지**.
- 타 도메인(member·action·team·project) 엔티티 import **금지**. 크로스도메인은 **id(Long) + 포트로만**.
- **스냅샷 원칙**: 신규 팀장 이름/직급은 이관 시점에 `OrgQueryPort.findMember`로 1회 조회해 handover에 저장. 조회 시 재조회 금지.
- 기존 정상 오프보딩/휴직 경로(SUBMITTED→REASSIGNED→FINALIZED)는 **회귀 없이 그대로 통과**해야 한다.
- 에러는 크게 터지게(박종준 선호). 미구현 크로스포트는 조용한 fallback 금지 — `HandoverErrorCode`로 명시적 throw.
- Flyway: 다음 버전 **V7.6** (내 레인 V7.x). 컬럼 추가는 additive nullable만. 기존 마이그레이션 수정 금지.

---

## 2. 도메인 변경

### 2-1. 새 상태 `PENDING_ATTRIBUTION` (귀속 대기)

`HandoverStatus`에 값 추가:

```
SUBMITTED, PENDING_ATTRIBUTION, REASSIGNED, FINALIZED, REJECTED
```

**상태머신 (팀장 오프보딩 분기 추가):**

```
정상(휴직/일반 오프보딩):  SUBMITTED → REASSIGNED → FINALIZED
팀장 오프보딩(후임 없음):  SUBMITTED → PENDING_ATTRIBUTION → FINALIZED
```

의미:
- `PENDING_ATTRIBUTION` = **오너가 퇴사를 승인(팀장은 이미 offboard/soft delete) + 문서·PDF·인사이트는 확정 + 액션만 새 팀장 대기 중**.
- `FINALIZED` = 두 경로 모두의 진짜 종료 상태 = **액션까지 새 주인을 찾음**.

> FE 참고: FE 메모의 "step2 finalize → FINALIZED"와 달리, **후임 없는 팀장 오프보딩에서 finalize의 착지점은 `PENDING_ATTRIBUTION`** 이다(후임 있으면 기존대로 FINALIZED). 화면에선 PENDING_ATTRIBUTION·FINALIZED 둘 다 "승인 완료"로 묶어 표시하고, PENDING_ATTRIBUTION일 때만 "귀속 대기 / 새 팀장 지정" 액션을 노출하면 된다.

### 2-2. `Handover` 신규 필드 (스냅샷)

추가:
- `boolean leaderHandover` — 생성 시점에 작성자가 그 팀의 팀장이면 true. `OrgQueryPort.findTeamLeaderId(teamId).equals(writerMemberId)`로 판정해 스냅샷 저장.
- `Long newLeaderId`, `String newLeaderNameSnap`, `String newLeaderPositionSnap`, `LocalDateTime attributedAt` — 이관 확정 시 채움(감사 기록).

`finalApproverId/finalApproverNameSnap` = **퇴사를 승인한 오너**(귀속 대기 진입 시 기록). `finalizedAt` = **최종 FINALIZED 도달 시각**(정상 경로=finalize 시각, 팀장 경로=이관 확정 시각).

### 2-3. `Handover` 신규/변경 메서드

**팩토리**: `createOffboarding(...)`에 `leaderHandover` 파라미터 추가(또는 별도 세터로 생성 직후 세팅). 도메인 불변식은 기존 유지.

**`finalizeAsPendingAttribution(ownerId, ownerNameSnap, at)`**
- guard: `status == SUBMITTED && handoverType == OFFBOARDING && leaderHandover == true`. 위반 시 `HO_PENDING_ATTRIBUTION_NOT_ALLOWED`.
- `status = PENDING_ATTRIBUTION`, `finalApproverId/NameSnap` = 오너, `intermediateApprovedAt` 등은 건드리지 않음. (finalizedAt은 아직 null — 아직 진짜 종료 아님)

**`attributeToNewLeader(newLeaderId, nameSnap, positionSnap, at)`**
- guard: `status == PENDING_ATTRIBUTION`. 위반 시 `HO_ATTRIBUTE_NOT_ALLOWED`.
- 모든 **reassignRequired 항목**에 대해 항목의 reassignee를 새 팀장으로 세팅(내부적으로 `HandoverItem.reassignTo(...)` 직접 호출 — 기존 `reassignItem`은 SUBMITTED 가드가 있으므로 **그걸 통하지 말고** 애그리거트 내부에서 항목을 직접 순회).
- `newLeaderId/newLeaderNameSnap/newLeaderPositionSnap/attributedAt` 세팅.
- `status = FINALIZED`, `finalizedAt = at`.

**`reject(reason)` 가드 보강**: `PENDING_ATTRIBUTION`에서도 반려 금지(작성자는 이미 offboard됨). 기존 `FINALIZED || REJECTED` 조건에 `PENDING_ATTRIBUTION` 추가.

---

## 3. 애플리케이션 서비스 (`HandoverService`)

### 3-1. `create` — leaderHandover 스냅샷
OFFBOARDING이면 `OrgQueryPort.findTeamLeaderId(teamId)` 조회 → `writerMemberId`와 같으면 `leaderHandover=true`로 생성. VACATION은 항상 false.

### 3-2. `finalize` 분기 (엔드포인트는 기존 `PATCH /{id}/finalize` 재사용)
로드 후 상태로 분기:
- `status == REASSIGNED` → 기존 `finalizeApproval(...)` 경로(정상). OFFBOARDING이면 기존대로 `offboard(writer)` + `finalizeInsights`.
- `status == SUBMITTED && leaderHandover && OFFBOARDING` → `handover.finalizeAsPendingAttribution(owner, ownerName, now)`
  - 이 전환에서 **팀장 퇴사 발생**: `memberStatusPort.offboard(writer)` 호출.
  - **인사이트 조립**: `finalizeHandoverInsightsUseCase.finalizeInsights(...)`를 여기서 실행(정상 경로에서 finalize에 있던 것을 이 분기로).
- 그 외(SUBMITTED 비팀장 등) → 도메인이 `HO_FINALIZE_NOT_ALLOWED` throw(기존 유지).

> 정상 오프보딩(비팀장)의 offboard+insights 타이밍은 **바뀌지 않는다**(여전히 REASSIGNED→FINALIZED에서). 팀장 경로만 SUBMITTED→PENDING_ATTRIBUTION에서 offboard+insights.

### 3-3. `attributeToNewLeader(handoverId, ownerId, newLeaderId, now)` — 신규 UseCase
1. 로드 → guard(PENDING_ATTRIBUTION).
2. `OrgQueryPort.findMember(newLeaderId)`로 신규 팀장 스냅샷.
3. `handover.attributeToNewLeader(newLeaderId, name, position, now)` — 항목 일괄 세팅 + FINALIZED.
4. 각 reassignRequired 항목에 대해 `ActionReassignPort.reassign(actionId, fromMemberId=writer(퇴사 팀장), toMemberId=newLeaderId)` 커밋(= 기존 `complete`의 커밋 로직과 동일 패턴).
5. `handoverRepository.save`.
> `memberStatusPort` 재호출·재offboard 금지(이미 PENDING 진입 시 offboard됨). insights 재조립 금지.

새 UseCase 인터페이스 `AttributeHandoverToLeaderUseCase` + `HandoverService`에 구현 추가.

---

## 4. 조회 (귀속 대기 목록)

### `GetPendingAttributionListUseCase` (신규) 또는 기존 리스트 확장
- 계약: 오너의 회사 범위 안에서 `status == PENDING_ATTRIBUTION`인 handover만 반환.
- 스코핑: 기존 오너 큐 설계(`OrgQueryPort.findMemberIdsByCompany(companyId)` → writer로 필터)와 동일 패턴 사용. **companyId는 auth(B) 미완이므로 v1은 `@RequestParam Long companyId` + `// TODO: auth(B) 도입 후 JWT claim으로 대체`** (기존 `list()`의 TODO 스타일과 동일).
- Repository 계약 추가: `List<Handover> findByStatus(HandoverStatus status)` 또는 `findByWriterMemberIdInAndStatus(Collection<Long>, HandoverStatus)`.
- 응답: **기존 `HandoverSummaryResponse` 재사용**(itemCount/reassignRequiredCount/reassignedCount로 FE가 "이관 대기 N건" 표시 가능). 새 DTO 불필요.

---

## 5. Presentation (컨트롤러 2종 추가)

`HandoverController`에 추가:

**A. 귀속 대기 목록**
```
GET /api/handovers/pending-attribution?companyId={companyId}
→ ApiResponse<List<HandoverSummaryResponse>>
```

**B. 신규 팀장 일괄 이관**
```
PATCH /api/handovers/{id}/attribute-to-leader
body: { "newLeaderId": <Long, @NotNull> }
principal: @AuthenticationPrincipal(expression="memberId") → 이관 실행 오너(감사용)
→ ApiResponse<HandoverResponse>   // items 전부 reassignee=새 팀장, status=FINALIZED
```

- 신규 요청 DTO `AttributeToLeaderRequest(record){ @NotNull Long newLeaderId; }` + `toCommand(handoverId, ownerId, at)`.
- 신규 커맨드 `AttributeHandoverToLeaderCommand(handoverId, ownerId, newLeaderId, attributedAt)`.

> **FE 설계 질문 답**: 기존 `PATCH .../items/{actionId}/reassign`(건별)은 **정상 오프보딩의 DnD 재분배용으로 의도된 설계**다. 팀장 귀속 이관에 이걸 N번 반복 호출하는 건 **의도가 아니다** — B 엔드포인트(일괄)를 써라. `orphanAlert`/`askWhom`은 "누구에게 물어볼지" 추천(읽기)이고, 실제 확정 이관의 쓰기 API가 바로 B다(단, 일괄 대상은 단일 신규 팀장 1명 — 이후 세부 재분배는 새 팀장이 액션 도메인에서 수행, 이번 범위 밖).

---

## 6. 인프라 / 마이그레이션

- `HandoverJpaEntity`: `is_leader_handover`(boolean, not null default false), `new_leader_id`, `new_leader_name_snap`, `new_leader_position_snap`, `attributed_at` 컬럼 + 매핑 추가. `restore/fromDomain/toDomain` 확장.
- `HandoverPersistenceAdapter`/`SpringDataHandoverRepository`: `findByStatus`(또는 IN+status) 추가.
- **V7.6__add_leader_attribution_to_handover.sql**:
  ```sql
  ALTER TABLE handover
    ADD COLUMN is_leader_handover     TINYINT(1)   NOT NULL DEFAULT 0,
    ADD COLUMN new_leader_id          BIGINT       NULL,
    ADD COLUMN new_leader_name_snap   VARCHAR(255) NULL,
    ADD COLUMN new_leader_position_snap VARCHAR(255) NULL,
    ADD COLUMN attributed_at          DATETIME(6)  NULL;
  ```
  (컬럼 타입은 기존 V7.x 컨벤션에 맞춰 최종 확정. `status` 컬럼은 VARCHAR(20)이라 enum 값 추가에 **마이그레이션 불필요**.)

---

## 7. 에러코드 추가 (`HandoverErrorCode`, HO-025~)

- `HO_PENDING_ATTRIBUTION_NOT_ALLOWED` (409) — 팀장 오프보딩·SUBMITTED가 아닌데 귀속 대기 전환 시도.
- `HO_ATTRIBUTE_NOT_ALLOWED` (409) — PENDING_ATTRIBUTION이 아닌데 일괄 이관 시도.
- `HO_ATTRIBUTE_COMMAND_INVALID` (400) — 이관 커맨드/`newLeaderId` 누락.

---

## 8. 테스트 (정상 + 예외, 로컬 100% 통과)

**도메인 `HandoverTest`**
- 팀장 오프보딩: SUBMITTED에서 `finalizeAsPendingAttribution` → PENDING_ATTRIBUTION, finalApprover 기록, finalizedAt 아직 null.
- 비팀장 오프보딩에서 `finalizeAsPendingAttribution` 호출 → `HO_PENDING_ATTRIBUTION_NOT_ALLOWED`.
- PENDING_ATTRIBUTION에서 `attributeToNewLeader` → 전 항목 reassignee=새팀장, status=FINALIZED, attributedAt/newLeader 스냅샷 세팅.
- PENDING_ATTRIBUTION이 아닌 상태에서 `attributeToNewLeader` → `HO_ATTRIBUTE_NOT_ALLOWED`.
- PENDING_ATTRIBUTION에서 `reject` 거부.
- **회귀**: 정상 경로(REASSIGNED→finalize→FINALIZED)·휴직·reject 기존 테스트 전부 통과.

**서비스 `HandoverServiceTest`** (Mockito)
- `create`(팀장 offboarding): `findTeamLeaderId` 매칭 → leaderHandover=true 저장.
- `finalize`(SUBMITTED·팀장): `offboard(writer)` + `finalizeInsights` 호출·PENDING_ATTRIBUTION.
- `finalize`(REASSIGNED·비팀장): 기존대로 offboard+insights·FINALIZED (회귀).
- `attributeToNewLeader`: `findMember(newLeader)` 스냅샷, 각 필수항목 `ActionReassignPort.reassign(actionId, writer, newLeader)` 호출, FINALIZED, offboard/insights **재호출 안 됨**.
- 목록: PENDING_ATTRIBUTION 필터.

**컨트롤러 `HandoverControllerTest`(@WebMvcTest)**: 두 엔드포인트 200/검증 실패 케이스.

---

## 9. 완료 기준 (Codex 자체 검증)

1. `./gradlew compileJava compileTestJava test` → **BUILD SUCCESSFUL**.
2. `handover` 패키지 **밖** 파일 생성·수정 **0건**(마이그레이션 sql은 예외 = handover 소유 V7.x 레인).
3. 타 도메인 엔티티 import 0, `global.common`/`SecurityConfig` 수정 0.
4. 기존 handover 테스트 **회귀 0**(정상 오프보딩/휴직/reject 그대로 통과).
5. 네이밍 컨벤션 준수(UseCase/Service/Command/Request/JpaEntity/PersistenceAdapter, `*Snap`, `leaderHandover`, `newLeaderId`, `attributedAt`).

---

## 10. FE에게 회신할 요약 (구현 후 이홍근에게 전달용)

- **귀속 대기 목록**: `GET /api/handovers/pending-attribution?companyId=…` → 기존 `HandoverSummaryResponse` 배열(status=`PENDING_ATTRIBUTION`).
- **일괄 이관 확정**: `PATCH /api/handovers/{id}/attribute-to-leader` body `{ newLeaderId }` → `HandoverResponse`(status=`FINALIZED`, 전 항목 reassignee=새 팀장).
- **finalize 결과값 주의**: 후임 없는 팀장 오프보딩의 `PATCH /{id}/finalize`는 `FINALIZED`가 아니라 `PENDING_ATTRIBUTION`을 반환한다. 이 상태에서만 "새 팀장 지정" 버튼 노출.
- 건별 `reassign`은 일반 재분배(DnD)용 — 팀장 이관에는 쓰지 말 것.
</content>
</invoke>
