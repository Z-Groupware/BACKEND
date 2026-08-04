package com.module06.backend.action.application.policy;

/* comment.
    FR-AC-07 — 팀 액션 상태 변경은 해당 팀의 LEADER만 가능하다는 권한 규칙.
    같은 팀 소속이어도 일반 MEMBER는 막히고, 다른 팀의 LEADER도 막힌다(팀 스코프 일치 필요).
    권한 판단은 인증 주체(JWT claim)를 봐야 하므로 domain이 아닌 application에 둔다.
    Controller의 @PreAuthorize가 1차 차단, 이 정책이 2차 방어(5.2절 방어벽 구분).

    연결된 클래스
    - TeamActionService : 팀 액션 상태 변경(단건·벌크) 시 호출
    - ActionErrorCode   : 위반 시 던질 에러 코드 (action.exception)
*/
public class TeamActionLeaderOnlyPolicy {
}
