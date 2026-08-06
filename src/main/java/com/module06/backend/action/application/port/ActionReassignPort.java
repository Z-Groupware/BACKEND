package com.module06.backend.action.application.port;

import java.time.LocalDate;
import java.util.List;

/* comment.
    action(C)이 선언하고, handover(E, 박종준) 도메인이 (위임 어댑터를 통해) 호출하는 인바운드 포트.
    구성원 인수인계(휴직·퇴사) 시 담당자 교체·인계 대상 조회를 위한 경계다.
    findTeamActionsForDeparture로 퇴사자가 관여한 팀 액션(고아경보 소스)을 조회하고,
    findHandoverableActions로 개인 인계 대상을 조회(휴직=미완료만/퇴사=완료포함)하며,
    reassign으로 개인 액션 담당자를 개별 단위로 교체하는 흐름으로 08/03 박종준(PO)과 확정했다.
    projectTag·sourceMeetingTitle(크로스도메인)·완료-프로젝트 제외 필터는 여기서 채우지 않고
    E가 스냅샷 조립 시 보강한다(08/06 확정).

    연결된 클래스
    - Action              : 담당자(assigneeMemberId)를 교체당하는 도메인 모델
    - ActionRepository    : 담당자 교체 대상 조회·갱신
    - ActionReassignService : 이 포트의 구현체 (application.service)
*/
public interface ActionReassignPort {

    List<HandoverableActionView> findHandoverableActions(Long memberId, HandoverScope scope);

    void reassign(Long actionId, Long fromMemberId, Long toMemberId);

    List<HandoverableActionView> findHandoverableActions(Long memberId);

    List<TeamActionForDepartureView> findTeamActionsForDeparture(Long memberId);

    enum HandoverScope {
        VACATION,
        OFFBOARDING
    }

    record HandoverableActionView(
            Long actionId,
            String title,
            Long projectId,
            String actionType,
            String status,
            LocalDate deadline,
            Long sourceMeetingId,
            String content
    ) {
    }

    record TeamActionForDepartureView(
            Long actionId,
            String title,
            Long projectId,
            Long sourceMeetingId,
            String status,
            Long teamId
    ) {
    }
}
