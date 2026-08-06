# Codex 구현 브리프 — E 인수인계·휴직 (handover 도메인)

> ⏳ **설계 시점 스냅샷 (2026-08-06 보존)** — 이 문서는 E 모듈 설계·기획 과정의 기록이며 최종 구현이 아니다. 예외 정책·상태 enum·응답 필드·경계식 등 실제 계약은 그 뒤 `develop` 코드에서 확정됐다. **정본은 항상 `develop`의 코드**이며, 문서와 코드가 다르면 코드가 옳다.

> 대상: `com.module06.backend.handover` 패키지 **한정**. 계약 근거=`docs/E-인수인계휴직-계약ERD초안.md`.
> 스택 Spring Boot 3 / JPA / Java 17 / Lombok. 클린아키텍처+DDD, 헥사고날.

## 0. 경계 — 반드시 지킬 것 (위반 시 실패)

- **오직 `handover` 패키지 안에서만** 파일 생성/수정. 그 밖(다른 도메인, 루트 설정)은 **절대 손대지 말 것**.
- **공용(shared) 금지**: `global.common`(ApiResponse·ErrorCode·BaseEntity·GlobalExceptionHandler) 생성·수정 금지, 공유 `ErrorCode` enum 수정 금지, `SecurityConfig` 금지.
- **타 도메인(member·action·team·project…) 엔티티 import 절대 금지.** 크로스도메인은 **id(Long)만** 필드로, **포트로만** 접근.
- **이번 범위 제외**: 컨트롤러(presentation), PDF 생성, 알림, `@PreAuthorize`. (공용 준비 후 별도)
- **예외 처리**: 도메인 규칙 위반은 `IllegalArgumentException`(400)/`IllegalStateException`. `BusinessException`/`ErrorCode.HO_*/LV_*`는 **주석 TODO만** 남기고 실제 참조하지 말 것(공용 미존재).
- **스냅샷 원칙**: 타 도메인 표시값(액션 제목/상태, 사람 이름/직급)은 **생성·재분배 시점에 포트로 1회 조회해 handover에 저장**. 조회(상세) 시 크로스도메인 재조회 금지.
- 타 도메인 소유 enum(액션 상태/유형)은 **String으로 스냅샷 저장**(C의 enum에 결합 금지).

## 1. 기존 파일 = 재구성 대상

현재 `handover/domain/`에 `@Entity`가 박힌 `Handover`,`HandoverItem` + enum + `application/port/out/`에 포트 2개가 있음(Claude 초안). **컨벤션대로 재구성**: 순수 도메인 모델 ↔ JpaEntity **분리**, 아래 구조로 이동. 상태머신 **로직은 재활용**하되 위치·시그니처·필드는 이 브리프 기준으로 교체.

## 2. 목표 패키지 구조

```
com.module06.backend.handover
├── application
│   ├── command      CreateHandoverCommand / ReassignItemCommand / RejectHandoverCommand (record)
│   ├── port/out     ActionReassignPort / OrgQueryPort / MemberStatusPort
│   ├── usecase      CreateHandoverUseCase / ReassignHandoverItemUseCase / CompleteHandoverUseCase / FinalizeHandoverUseCase / RejectHandoverUseCase
│   └── service      HandoverService (위 UseCase 구현, @Transactional)
├── domain
│   ├── model        Handover / HandoverItem / HandoverType / HandoverStatus
│   └── repository   HandoverRepository (도메인 저장소 계약)
└── infrastructure
    └── persistence  HandoverJpaEntity / HandoverItemJpaEntity / SpringDataHandoverRepository / HandoverPersistenceAdapter
```

## 3. 도메인 모델 (순수 — JPA 애노테이션 금지)

**HandoverType**: `VACATION`, `OFFBOARDING`
**HandoverStatus**: `SUBMITTED` → `REASSIGNED` → `FINALIZED`, 그리고 `REJECTED`(반려, 행 보존+사유)

**Handover** (애그리거트 루트)
- 필드: `id`, `writerMemberId`, `teamId`, `handoverType`, `status`, `leaveStartAt`(LocalDateTime, VACATION만), `leaveEndAt`, `writerNameSnap`, `writerPositionSnap`, `intermediateApproverId`, `intermediateApproverNameSnap`, `intermediateApprovedAt`, `rejectReason`, `finalizedAt`, `items`(List<HandoverItem>)
- 팩토리: `createVacation(writerMemberId, teamId, writerNameSnap, writerPositionSnap, leaveStartAt, leaveEndAt, items)` — 날짜 필수·end≥start / `createOffboarding(writerMemberId, teamId, writerNameSnap, writerPositionSnap, items)` — 기간 null
- 생성 즉시 `status=SUBMITTED`
- 메서드·불변식:
  - `reassignItem(actionId, toMemberId, reassigneeNameSnap, reassigneePositionSnap, at)` — **SUBMITTED에서만**, 해당 actionId 항목에 스냅샷 세팅
  - `complete(approverId, approverNameSnap, at)` — **SUBMITTED & 전 항목 재분배 완료**일 때만 → `REASSIGNED`, 중간승인자 스냅샷 기록
  - `finalizeApproval(at)` — **REASSIGNED에서만** → `FINALIZED` + finalizedAt
  - `reject(reason)` — **FINALIZED 아닐 때만** → `REJECTED` + rejectReason(필수, 공백 금지)
  - `isAllReassigned()` — 항목 없으면 true

**HandoverItem**
- 필드: `id`, `actionId`, `actionTitleSnap`, `actionStatusSnap`(String), `projectTagSnap`(String, null 허용), `actionTypeSnap`(String), `reassigneeId`(null=미분배), `reassigneeNameSnap`, `reassigneePositionSnap`, `reassignedAt`
- `reassignTo(memberId, nameSnap, positionSnap, at)`, `isReassigned()`(reassigneeId!=null)

## 4. 아웃 포트 (application/port/out) — E 정의, 이웃 구현

```java
interface ActionReassignPort {
  List<HandoverableAction> findHandoverableActions(Long memberId, HandoverType type); // 미완료만
  void reassign(Long actionId, Long fromMemberId, Long toMemberId);                    // 개별 액션 단위
  record HandoverableAction(Long actionId, String title, String projectTag,
                            String actionType, String status, LocalDate deadline) {}
}
interface OrgQueryPort {
  Long findTeamLeaderId(Long teamId);
  MemberSnapshot findMember(Long memberId);
  List<ReassignCandidate> findReassignCandidates(Long teamId, Long excludeMemberId);   // 같은 팀·작성자 제외
  record MemberSnapshot(Long memberId, String name, String position) {}
  record ReassignCandidate(Long memberId, String name, String position, int actionCount) {}
}
interface MemberStatusPort { // intent 기반 (member enum 참조 금지)
  void toWaiting(Long memberId); void toVacation(Long memberId);
  void offboard(Long memberId); void restoreActive(Long memberId);
}
```

## 5. 애플리케이션 서비스 (오케스트레이션, @Transactional)

- **생성** `create(cmd)`: `OrgQueryPort.findMember(writer)`로 작성자 스냅샷 → `ActionReassignPort.findHandoverableActions(writer, type)` (VACATION=cmd.selectedActionIds로 필터 / OFFBOARDING=전체) → 각 액션 스냅샷으로 항목 구성 → 팩토리 생성 → `repository.save` → `MemberStatusPort.toWaiting(writer)`.
- **재분배** `reassignItem(handoverId, actionId, toMemberId)`: 로드 → `OrgQueryPort.findMember(toMemberId)`로 대상 스냅샷 → `handover.reassignItem(...)` → save.
- **완료** `complete(handoverId, leaderId)`: 로드 → 리더 스냅샷 → `handover.complete(...)` → **항목별 `ActionReassignPort.reassign(actionId, writer, reassignee)`** 커밋 → save.
- **최종승인** `finalize(handoverId)`: 로드 → `handover.finalizeApproval(now)` → VACATION이면 `MemberStatusPort.toVacation(writer)`, OFFBOARDING이면 `offboard(writer)` → save.
- **반려** `reject(handoverId, reason)`: 로드 → `handover.reject(reason)` → `MemberStatusPort.restoreActive(writer)` → save(행 보존).
- 시각(now)은 파라미터로 주입(테스트 결정성). Clock 또는 LocalDateTime 인자.

## 6. 인프라 (infrastructure/persistence)

- `HandoverJpaEntity`, `HandoverItemJpaEntity`: JPA 매핑. handover↔item만 `@OneToMany/@ManyToOne`(같은 도메인 강결합). 타 도메인은 id 컬럼만. `@CreationTimestamp/@UpdateTimestamp` 인라인(+`// TODO: global BaseTimeEntity 이관`).
- 테이블/컬럼명은 계약 문서 §2 스네이크케이스.
- `SpringDataHandoverRepository extends JpaRepository<HandoverJpaEntity, Long>`.
- `HandoverPersistenceAdapter implements HandoverRepository`(domain 계약) — 도메인↔JpaEntity 매핑. 계약 메서드: `save(Handover)`, `findById(Long)→Optional<Handover>`, `findByWriterMemberId(Long)`, `findByTeamIdAndStatus(Long, HandoverStatus)`.

## 7. 테스트 (정상 + 예외, 로컬 100% 통과)

- **도메인** `HandoverTest`(JUnit5): 상태머신 불변식 — 휴직 날짜검증 / 오프보딩 기간null / 전항목 재분배 전 complete 거부 / SUBMITTED 아닐 때 재분배 거부 / REASSIGNED 전 finalize 거부 / finalize→FINALIZED / FINALIZED 반려 거부 / reject→REJECTED+사유 / 빈사유 거부.
- **서비스** `HandoverServiceTest`: Mockito `@Mock`으로 3 포트 + `HandoverRepository`. 전 플로우 검증 — 생성 시 `toWaiting` 호출·작성자/액션 스냅샷 저장, 재분배 후 complete 시 `ActionReassignPort.reassign` 호출·REASSIGNED, finalize(VACATION)→`toVacation`/OFFBOARDING→`offboard`, reject→REJECTED+`restoreActive`+사유.
- (선택) `@DataJpaTest` 영속성 슬라이스(H2)로 저장·조회 왕복.

## 8. 완료 기준 (Codex 자체 검증)

1. `./gradlew compileJava compileTestJava test` → **BUILD SUCCESSFUL**
2. `handover` 패키지 **밖** 파일 생성·수정 **0건**
3. 타 도메인 엔티티 import 0, `global.common` import 0 (예외는 IllegalArgument/State만)
4. 네이밍: JpaEntity·PersistenceAdapter·Repository(계약)·UseCase·Service·Command·Port 접미사 준수. 변수명 `handoverType`·`leaveStartAt/leaveEndAt`·`reassigneeId`·`*Snap`.
