# E(인수인계·휴직) — develop PR 분할 계획

> 작성 2026-08-04 · 담당 박종준(PO). 통합본(단일 커밋 `fedfdd0`, test 116 그린)을 기능별로 쪼개 develop에 PR.
> 원칙: 세 /네 브랜치 모두 **develop에서 분기**, 스택 순서(뒤 PR은 앞 PR 머지 후). 각 PR 단독 빌드 그린.
> C/D/B 포트는 인터페이스만 있고 구현체가 없어도 컨텍스트 정상 부팅(Optional/ObjectProvider 부재 처리) → **전부 지금 머지 가능**.

## PR1 — `feat/mo-handover-core` (인수인계·휴직 베이스)
신청·재분배·중간승인·최종승인·반려 상태머신 + 영속성.
- `domain/model/*`(Handover, HandoverItem, HandoverStatus, HandoverType) + `domain/repository/HandoverRepository`
- `application/command/*`(Create/Reassign/Reject) · `usecase/*`(5종) · `service/HandoverService`
- `port/out/ActionReassignPort·OrgQueryPort·MemberStatusPort` (**베이스 메서드만**)
- `infrastructure/persistence/*`(JPA 엔티티·어댑터·리포지토리)
- 마이그레이션 `V7.0~V7.3`, `V7.5`(reassign_required) — V7.4(인사이트)는 PR3에서 추가
- `src/test/resources/application.yaml` (**test flyway off** — 팀 공용, 여기 포함 필수)
- 테스트 `HandoverTest`, `HandoverServiceTest`
- ⚠️ 이 PR의 finalize엔 인사이트 호출 없음(PR3에서 추가)

## PR2 — `feat/mo-handover-package` (인계 패키지 조회) ← PR1 의존
후임자용 인계서 조회 read model.
- `application/usecase/GetHandoverPackageUseCase` · `service/HandoverPackageService`
- `port/out/MeetingQueryPort`(**findMeeting 베이스만**)
- 테스트 `HandoverPackageServiceTest`

## PR3 — `feat/mo-handover-insight` (레거시 컴파일러) ← PR1(+PR2) 의존
차별점 파생 인텔리전스. 발표 헤드라인.
- `domain/model/HandoverInsight·HandoverInsightKind`
- `application/command/FinalizeHandoverInsightsCommand` · `usecase/FinalizeHandoverInsightsUseCase` · `service/HandoverInsightFinalizeService`
- `port/out/HandoverInsightPort` + **ActionReassign/Org/Meeting 포트에 인사이트 메서드 증분 추가**
- `infrastructure/persistence/HandoverInsight*`(엔티티·리포·어댑터)
- 마이그레이션 `V7.4`, `build.gradle`(jackson-jsr310)
- 배선: `HandoverService.finalize` 수정 + `HandoverServiceTest` 갱신(인사이트 목·projectId)
- 테스트 `HandoverInsightFinalizeServiceTest`, 문서 `docs/E-레거시컴파일러…`

## PR4 — `feat/mo-handover-api` (REST 컨트롤러) ← PR1~3 의존
공용 스켈레톤(ApiResponse·ErrorCode·GlobalExceptionHandler)이 develop에 존재 → presentation 착수.
- `presentation/api/*` 컨트롤러(신청/재분배/완료/최종승인/반려/조회)
- `presentation/api/request·response/*` DTO
- 공용 `ErrorCode`에 `HO_*`/`LV_*` 추가 + 도메인 TODO 예외 → `BusinessException` 치환
- 컨트롤러 슬라이스 테스트

## 의존성 요약
```text
develop ─ PR1(core) ─ PR2(package) ─ PR3(insight) ─ PR4(api)
```
포트 3종은 PR1 베이스 시그니처 → PR3 인사이트 메서드 증분. PR4에서 REST 노출.
더 적게 가려면 PR1+PR2 합쳐 3개로도 가능.
