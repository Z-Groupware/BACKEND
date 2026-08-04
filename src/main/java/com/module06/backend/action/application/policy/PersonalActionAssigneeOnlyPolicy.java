package com.module06.backend.action.application.policy;

/* comment.
    FR-AC-03,05 — 개인 액션 상태 변경·체크리스트 CRUD는 담당자 본인만 가능하다는 권한 규칙.
    타 MEMBER는 물론 같은 프로젝트 OWNER·LEADER라도 담당자가 아니면 막힌다.
    권한 판단은 인증 주체(JWT claim)를 봐야 하므로 domain이 아닌 application에 둔다.
    Controller의 @PreAuthorize가 1차 차단, 이 정책이 2차 방어(5.2절 방어벽 구분).

    연결된 클래스
    - ActionService          : 개인 액션 상태 변경(단건·벌크)·AI 검토 확정 시 호출
    - ActionChecklistService : 체크리스트 CRUD 시 호출
    - ActionErrorCode        : 위반 시 던질 에러 코드 (미생성)
*/
public class PersonalActionAssigneeOnlyPolicy {
}
