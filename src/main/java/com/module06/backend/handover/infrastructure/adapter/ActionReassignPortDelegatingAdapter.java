package com.module06.backend.handover.infrastructure.adapter;

import com.module06.backend.handover.application.port.out.ActionReassignPort;
import com.module06.backend.handover.domain.model.HandoverType;

import java.util.List;

import org.springframework.stereotype.Component;

/* comment.
    E(handover) 아웃포트 ActionReassignPort를 구현하되, 실제 로직은 C(action) 인바운드 포트
    action.application.port.ActionReassignPort에 위임하고 타입만 매핑하는 위임 어댑터.
    D(meeting)의 MeetingQueryPortDelegatingAdapter와 동일 패턴 — 도메인 경계는 포트로만 넘고
    엔티티/서비스는 직접 참조하지 않는다.
    PendingAdapter(전부 throw)를 대체해 handover의 생성/완료/최종승인·인사이트 흐름을 실배선한다.
    projectTag·sourceMeetingTitle은 action이 못 채우는 크로스도메인 필드라 여기서는 null로 두고,
    handover 스냅샷 조립 단계에서 OrgQueryPort/MeetingQueryPort로 보강한다(08/06 박종준 확정) —
    silent fallback이 아니라 의도된 위임 지점이다.
    회사 스코프: action 인바운드 포트가 companyId를 받지 않으므로 이 경계에서 회사 스코프를 강제하지 않는다.
    memberId/actionId는 전역 유니크 전제이며 회사 경계는 상위 handover 서비스에서 보장한다.

    연결된 클래스
    - com.module06.backend.handover.application.port.out.ActionReassignPort : 구현하는 E 아웃포트 계약
    - com.module06.backend.action.application.port.ActionReassignPort       : 위임 대상 C 인바운드 포트
    - HandoverType : VACATION/OFFBOARDING → action HandoverScope 매핑
*/
@Component
public class ActionReassignPortDelegatingAdapter implements ActionReassignPort {

    private final com.module06.backend.action.application.port.ActionReassignPort actionReassignPort;

    public ActionReassignPortDelegatingAdapter(
            com.module06.backend.action.application.port.ActionReassignPort actionReassignPort
    ) {
        this.actionReassignPort = actionReassignPort;
    }

    @Override
    public List<HandoverableAction> findHandoverableActions(Long memberId, HandoverType type) {
        return actionReassignPort.findHandoverableActions(memberId, toScope(type)).stream()
                .map(this::toHandoverableAction)
                .toList();
    }

    @Override
    public void reassign(Long actionId, Long fromMemberId, Long toMemberId) {
        actionReassignPort.reassign(actionId, fromMemberId, toMemberId);
    }

    @Override
    public List<HandoverableAction> findHandoverableActions(Long memberId) {
        return actionReassignPort.findHandoverableActions(memberId).stream()
                .map(this::toHandoverableAction)
                .toList();
    }

    @Override
    public List<TeamActionForDeparture> findTeamActionsForDeparture(Long memberId) {
        return actionReassignPort.findTeamActionsForDeparture(memberId).stream()
                .map(this::toTeamActionForDeparture)
                .toList();
    }

    private com.module06.backend.action.application.port.ActionReassignPort.HandoverScope toScope(HandoverType type) {
        // handover.HandoverType과 action.HandoverScope는 값 이름이 1:1(VACATION/OFFBOARDING).
        return com.module06.backend.action.application.port.ActionReassignPort.HandoverScope.valueOf(type.name());
    }

    private HandoverableAction toHandoverableAction(
            com.module06.backend.action.application.port.ActionReassignPort.HandoverableActionView view
    ) {
        return new HandoverableAction(
                view.actionId(),
                view.title(),
                // projectTag: action 미소유 크로스도메인 필드 → null. handover 조립 단계에서 보강(의도된 위임).
                null,
                view.projectId(),
                view.actionType(),
                view.status(),
                view.deadline(),
                view.sourceMeetingId(),
                // sourceMeetingTitle: meeting(D) 소유 → null. handover 조립 단계에서 보강(의도된 위임).
                null,
                view.content()
        );
    }

    private TeamActionForDeparture toTeamActionForDeparture(
            com.module06.backend.action.application.port.ActionReassignPort.TeamActionForDepartureView view
    ) {
        return new TeamActionForDeparture(
                view.actionId(),
                view.title(),
                view.projectId(),
                view.sourceMeetingId(),
                view.status(),
                view.teamId()
        );
    }
}
