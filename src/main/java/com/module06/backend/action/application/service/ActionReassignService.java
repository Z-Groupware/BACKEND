package com.module06.backend.action.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.port.ActionReassignPort;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/* comment.
    action.application.port.ActionReassignPort 구현체. handover(E) 인수인계 흐름의
    인계 대상 조회·담당자 재분배를 action 테이블 범위 안에서만 처리한다(경계 유지).
    조회는 읽기 전용 트랜잭션, reassign은 쓰기 트랜잭션으로 가른다.
    도메인 규칙(08/03·08/06 박종준 확정): 휴직(VACATION)=개인 미완료만 / 퇴사(OFFBOARDING)·타입무관=개인 전체(완료 포함,
    완료-프로젝트 제외 필터는 E가 조립 시 처리) / reassign=PERSONAL만·fromMember 본인 스코프·개별 단위 /
    findTeamActionsForDeparture=퇴사자 개인 액션의 부모 TEAM 액션. record의 projectTag·sourceMeetingTitle은
    액션이 못 채우는 크로스도메인 필드라 노출하지 않는다(E가 보강).

    연결된 클래스
    - ActionReassignPort : 구현하는 인바운드 포트 (application.port)
    - ActionRepository   : 조회·저장 위임 (domain.repository)
    - ActionErrorCode    : 재분배 방어 위반 시 던지는 코드 (exception)
*/
@Service
@RequiredArgsConstructor
public class ActionReassignService implements ActionReassignPort {

    private final ActionRepository actionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<HandoverableActionView> findHandoverableActions(Long memberId, HandoverScope scope) {
        boolean excludeDone = scope == HandoverScope.VACATION;
        return actionRepository.findPersonalByAssignee(memberId, excludeDone)
                .stream()
                .map(this::toHandoverableActionView)
                .toList();
    }

    @Override
    @Transactional
    public void reassign(Long actionId, Long fromMemberId, Long toMemberId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));

        if (action.getActionType() == ActionType.TEAM) {
            throw new BusinessException(ActionErrorCode.CANNOT_REASSIGN_TEAM_ACTION);
        }
        if (action.getAssigneeMemberId() == null || !Objects.equals(action.getAssigneeMemberId(), fromMemberId)) {
            throw new BusinessException(ActionErrorCode.NOT_ACTION_ASSIGNEE);
        }

        action.reassignTo(toMemberId);
        actionRepository.save(action);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HandoverableActionView> findHandoverableActions(Long memberId) {
        return actionRepository.findAllPersonalByAssignee(memberId)
                .stream()
                .map(this::toHandoverableActionView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamActionForDepartureView> findTeamActionsForDeparture(Long memberId) {
        return actionRepository.findParentTeamActionsByAssignee(memberId)
                .stream()
                .map(this::toTeamActionForDepartureView)
                .toList();
    }

    private HandoverableActionView toHandoverableActionView(Action action) {
        return new HandoverableActionView(
                action.getId(),
                action.getTitle(),
                action.getProjectId(),
                action.getActionType().name(),
                action.getStatus().name(),
                action.getDueDate(),
                action.getSourceMeetingId(),
                action.getDescription()
        );
    }

    private TeamActionForDepartureView toTeamActionForDepartureView(Action action) {
        return new TeamActionForDepartureView(
                action.getId(),
                action.getTitle(),
                action.getProjectId(),
                action.getSourceMeetingId(),
                action.getStatus().name(),
                action.getTeamId()
        );
    }
}
