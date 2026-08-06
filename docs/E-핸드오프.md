# E 인수인계·휴직 — 핸드오프 (2026-08-03)

> 담당 박종준(PO) · 브랜치 `claude/handover-leave-offboarding-68caa1`
> 이 문서 = 지금까지의 결정·구현·열린 항목 종합. 상세는 각 참조 문서.

## 1. 범위
- **E 도메인**: 인수인계(handover) + 휴직/퇴사(leave/offboarding). 패키지 `com.module06.backend.handover`.
- **발표 차별점**: 퇴사버튼 = 자동 인수인계 패키지(축적된 회의 액션 → 자동 조립).
- **박종준 개인 심화**: AI 토큰 사용량 기반 과금(usage-based pricing) — E와 별개 조각. → `docs/토큰사용량-미터링-설계.md`

## 2. 확정 계약 (상세: `docs/E-인수인계휴직-계약ERD초안.md`)
- **아키텍처**: 스냅샷 + BE 조립. 표시값(액션 제목·상태, 사람 이름·직급)을 생성/재분배 시점에 포트로 조회해 handover에 찍어 저장 → 상세/PDF 조회 시 크로스도메인 실시간 조회 0.
- **상태머신**: `SUBMITTED → REASSIGNED(팀장 완료·중간승인) → FINALIZED(오너·어드민 최종승인)`, `REJECTED`(반려, 사유 보존).
- **멤버 상태**: `ACTIVE → WAITING(신청 즉시) → ON_LEAVE(휴직 최종승인)`, 반려 시 ACTIVE 원복. 삭제=`member.deleted_at` 소프트(status 아님).
- **PDF**: BE 미생성, FE가 상세 응답으로 발행. **알림**: 인수인계 알림 스코프 아웃(회의생성·10분전만).
- **크로스도메인 포트**(E 정의): `ActionReassignPort`(C), `OrgQueryPort`(B), `MemberStatusPort`(B), `MeetingQueryPort`(D, 퇴사패키지 §5용).

## 3. 역할·유형별 규칙 (§5.5, 최종)
- **액션 범위**: 휴직=미완료만 / 퇴사=참여한 **진행 중 프로젝트**의 전체 액션(완료 포함). 완료 액션=기록용(재분배 불필요, `reassignRequired=false`), 완료 게이트는 미완료만 요구.
- **팀장 휴직**=본인이 개인액션 후임 지정 / **팀장 퇴사**=오너가 다른 팀장에게 **수동 인계**.
- **새 팀장 지정**=오너(조직/auth, 종호) / **host 회의**=퇴사 시에만 **삭제**(D 도메인). 둘 다 E 밖.
- **일반 사원**=팀장이 팀원 중 재분배.

## 4. 구현 상태 (Codex 2라운드, Claude 검증 통과)
- 코어 + 퇴사 패키지 조립 뷰 구현 완료. `./gradlew ... test` BUILD SUCCESSFUL, 테스트 17개 통과.
- 코드: `domain/model`(Handover·HandoverItem·Enum2, 순수) · `domain/repository` · `application`(UseCase·Service·Command) · `application/port/out`(포트4) · `infrastructure/persistence`(JpaEntity2·Adapter·SpringData). 퇴사 패키지=`HandoverPackageService`+`GetHandoverPackageUseCase`.
- 경계: handover 밖 0건, global.common import 0, 컨트롤러/PDF/알림/@PreAuthorize 없음. 예외=IllegalArgument/State(BusinessException은 TODO).

## 5. 크로스도메인 협의 상태
- **C 액션(민섭)**: reassign=assignee 교체·개인 액션만, 체크리스트 자동 딸림, 오프보딩=진행중 프로젝트 전체 제공. ✅ 답변 완료
- **B 회원·조직(종호)**: MemberStatus 3종, offboard 멱등+한 트랜잭션, 역할강등·새팀장=종호. `handover_approval` 테이블 언급(E는 인라인 저장). ✅ 답변 완료
- **D 회의(모성진)**: `MeetingQueryPort`(§5) + host 회의 삭제. ⏳ 협의 대기
- **A 캡처(이태연·김현지)**: 토큰 사용량 방출(미터링용). ⏳ 협의 대기(토큰 조각)

## 6. 다음 Codex 라운드 예정 변경 (아직 코드 미반영)
1. 네이밍: `HandoverType.VACATION→LEAVE`, `MemberStatusPort.toVacation→toOnLeave`
2. 퇴사 완료 업무 기록용 포함(진행중 프로젝트 필터) + `reassignRequired` 플래그 + 게이트 수정
3. 팀장 라우팅(휴직=본인 / 퇴사=오너가 다른 팀장 수동)
4. 퇴사 패키지 테스트 보강(현재 1개)

## 7. 참조 문서
- `docs/E-인수인계휴직-계약ERD초안.md` — 계약·ERD·포트·규칙 (마스터)
- `docs/E-퇴사버튼-자동인수인계패키지.md` — 6블록 조립 뷰
- `docs/토큰사용량-미터링-설계.md` — 토큰 과금 (별개 조각)
- `docs/E-codex-brief.md`, `docs/E-codex-brief-2-패키지.md` — 구현 브리프
