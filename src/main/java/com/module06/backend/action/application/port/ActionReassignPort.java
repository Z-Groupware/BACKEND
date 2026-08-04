package com.module06.backend.action.application.port;

/* comment.
    action(C)이 선언하고, employee(E, 박종준) 도메인이 호출하는 인바운드 포트.
    구성원 인수인계(퇴사·부서이동) 시 담당자를 교체하기 위한 경계다.
    findTeamActionsForDeparture로 퇴사자가 속했던 팀의 팀 액션 목록을 먼저 조회한 뒤,
    개인 액션 담당자를 새 담당자로 교체하는 흐름으로 08/03 박종준(PO)과 확정했다.

    연결된 클래스
    - Action           : 담당자(assigneeMemberId)를 교체당하는 도메인 모델
    - ActionRepository : 담당자 교체 대상 조회·갱신
*/
public interface ActionReassignPort {
}
