# Codex 브리프 #2 — 퇴사버튼 자동 인수인계 패키지 (조립 읽기 뷰)

> ⏳ **설계 시점 스냅샷 (2026-08-06 보존)** — 이 문서는 E 모듈 설계·기획 과정의 기록이며 최종 구현이 아니다. 예외 정책·상태 enum·응답 필드·경계식 등 실제 계약은 그 뒤 `develop` 코드에서 확정됐다. **정본은 항상 `develop`의 코드**이며, 문서와 코드가 다르면 코드가 옳다.

> 대상: `com.module06.backend.handover` **한정**. 기존 구현(브리프 #1) 위에 **추가/확장**.
> 근거: `docs/E-퇴사버튼-자동인수인계패키지.md`, `docs/E-인수인계휴직-계약ERD초안.md`. 브리프 #1(`docs/E-codex-brief.md`)의 경계·컨벤션 전부 그대로 적용.

## 0. 경계 (브리프 #1과 동일, 재확인)
- `handover` 패키지 **밖 절대 손대지 말 것**. `global.common` 생성/수정 금지, 타 도메인 엔티티 import 금지.
- **컨트롤러 여전히 제외**(공용 `ApiResponse` 미존재). 이 패키지의 결과는 **애플리케이션 계층 결과 객체(record)**로 반환. presentation 만들지 말 것.
- 예외는 `IllegalArgumentException`/`IllegalStateException`(BusinessException은 TODO 주석).
- 도메인↔JpaEntity 분리 유지. 스냅샷 원칙 유지.
- **기존 테스트 계속 통과해야 함.** 필드 추가로 깨지는 기존 테스트는 함께 수정.

## 1. 도메인·영속성 필드 추가 (기존 파일 확장)
- `Handover`(+JpaEntity/adapter/restore): **`lastWorkingDay`(LocalDate, OFFBOARDING만, VACATION은 null)** 추가. 팩토리 `createOffboarding`에 파라미터 추가, `createVacation`은 lastWorkingDay 금지(있으면 IllegalArgument).
- `HandoverItem`(+JpaEntity/adapter): 스냅샷 컬럼 추가 — **`deadlineSnap`(LocalDate null)**, **`sourceMeetingId`(Long null)**, **`sourceMeetingTitleSnap`(String null)**, **`contentSnap`(String null, TEXT 매핑)**. 생성 팩토리 시그니처에 추가.

## 2. 포트 변경
- `ActionReassignPort.HandoverableAction` record에 필드 추가: **`LocalDate deadline`(이미 있음), `Long sourceMeetingId`, `String sourceMeetingTitle`, `String content`**.
- **신설 `MeetingQueryPort`** (application/port/out, D 도메인 구현):
```java
interface MeetingQueryPort {
  MeetingHistory findMeeting(Long meetingId);
  record MeetingHistory(Long meetingId, LocalDate date, List<String> attendees,
                        String decisionSummary, String actionItemsSummary) {}
}
```

## 3. 스냅샷 캡처 갱신
`HandoverService.snapshotItems`에서 항목 생성 시 `deadline·sourceMeetingId·sourceMeetingTitle·content`도 스냅샷으로 저장. 작성자 스냅샷은 그대로.

## 4. 조립 읽기 뷰 (신규 · 핵심)
- **UseCase** `GetHandoverPackageUseCase` (application/usecase): `HandoverPackage getPackage(Long handoverId, LocalDate referenceDate)`. 결과 record는 이 인터페이스에 **중첩 정의**(별도 presentation DTO 금지).
- **Service** `HandoverPackageService`(또는 기존 서비스에 추가) — `@Transactional(readOnly = true)`. 저장된 handover+items 스냅샷으로 블록 1~4·6 구성, §5만 `MeetingQueryPort` 라이브 조회.
- **HandoverPackage** 결과 구조(6블록):
  - **basicInfo**: writerName, writerPosition, teamId, absenceType(=handoverType), startDate(leaveStartAt 또는 상신일), returnDate(leaveEndAt), lastWorkingDay
  - **gapSummary**: totalItems, incompleteCount(상태!=완료), dueSoonCount(deadlineSnap ≤ referenceDate+7일 && 미완료)
  - **items**: [title, status, deadline, projectTag, sourceMeetingTitle] (handover_item 스냅샷)
  - **contextCards**: [title, contentSnap] (블록4, 액션 기존 AI 정리 재사용 — 새 생성 금지)
  - **meetingHistories**: items의 **distinct sourceMeetingId**마다 `MeetingQueryPort.findMeeting` 호출 → [date, attendees, decisionSummary, actionItemsSummary]
  - **reassigneeGroups**: reassigneeId로 그룹핑 → [reassigneeName, [items…]] (미분배는 별도 '미배정' 그룹 또는 제외)
- 순수 계산(카운트·그룹핑·D-7 판정)은 서비스 또는 도메인 헬퍼로. referenceDate는 **파라미터 주입**(테스트 결정성).

## 5. 잘라낸 것 (구현 금지)
§6 결정·§7 권한/자원, 추천 인수자·추천 이유, 새 AI 맥락 생성, 다자 인수완료 체크리스트. **절대 만들지 말 것.**

## 6. 테스트
- 기존 `HandoverTest`·`HandoverServiceTest`를 새 필드 시그니처에 맞게 수정(계속 통과).
- **신규** `HandoverPackageServiceTest`: Mockito로 `HandoverRepository`+`MeetingQueryPort` mock. 검증 — gapSummary 카운트(미완료·마감임박 D-7 경계), items/contextCards 매핑, distinct 회의만 `findMeeting` 호출, reassigneeGroups 그룹핑 정확.

## 7. 완료 기준
1. `./gradlew compileJava compileTestJava test` → **BUILD SUCCESSFUL** (기존+신규 테스트 전부 통과)
2. `handover` 패키지 밖 변경 0건, `global.common`·타 도메인 import 0, 컨트롤러/PDF/알림/@PreAuthorize 없음
3. 변경/신규 파일 목록 + 테스트 결과 + 가정 보고
