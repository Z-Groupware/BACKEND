package com.module06.backend.action.infrastructure.adapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.infrastructure.persistence.ActionMeetingReferenceEntity;
import com.module06.backend.action.infrastructure.persistence.ActionMeetingReferenceRepository;
import com.module06.backend.action.infrastructure.persistence.ProjectReferenceEntity;
import com.module06.backend.action.infrastructure.persistence.SpringDataProjectReferenceRepository;
import com.module06.backend.handover.application.port.out.ActionReassignPort;
import com.module06.backend.handover.domain.model.HandoverType;

import lombok.RequiredArgsConstructor;

/* comment.
    action(C)이 구현하는 인수인계(E) 아웃바운드 포트. E가 정의한 ActionReassignPort
    (handover.application.port.out)를 실제로 배선한다 — ActionReassignPortPendingAdapter를
    대체(2026-08-06, 종준 PO 요청).

    findHandoverableActions(memberId, type): VACATION(휴직)=미완료 개인 액션만,
    OFFBOARDING(퇴사)=상태 무관 전체(완료 포함) — 어느 쪽이든 완료된 프로젝트는 제외.
    findHandoverableActions(memberId)(오버로드, 인사이트 조립용): 타입 무관이라
    OFFBOARDING과 동일 스코프(전체+완료 프로젝트 제외)로 취급한다.
    findTeamActionsForDeparture(memberId): 이 memberId가 팀장인 팀들의 TEAM 액션 —
    고아경보(팀장 이탈 시 상태변경 권한자 소실) 판정용, 읽기 전용.
    reassign: PERSONAL 액션만 대상(Action.reassignTo가 강제), 담당자 일치 여부를 먼저 확인한다.
    findByIdForUpdate(SELECT ... FOR UPDATE)로 읽어 같은 트랜잭션(@Transactional) 안에서 바로
    쓴다 — 동시에 두 인수인계 요청이 같은 액션을 재배정하면, 잠금 없이는 나중에 커밋한 쪽이
    먼저 커밋한 쪽을 조용히 덮어쓴다(2026-08-08, CodeRabbit PR #126 지적 정리).
    rollbackReassignment: reassign의 역방향(인수인계 반려 시 원담당자 복원). 검증·저장 로직은
    reassign에 그대로 위임 — 대칭 연산이라 중복 구현 안 함(2026-08-10, 종준 PO 요청).

    연결된 클래스
    - ActionRepository                       : 실제 조회·저장 위임 대상
    - SpringDataProjectReferenceRepository   : projectTag 배치조회
    - SpringDataMeetingReferenceRepository   : sourceMeetingTitle 배치조회
*/
@Component
@RequiredArgsConstructor
public class ActionReassignAdapter implements ActionReassignPort {

    private final ActionRepository actionRepository;
    private final SpringDataProjectReferenceRepository springDataProjectReferenceRepository;
    private final ActionMeetingReferenceRepository actionMeetingReferenceRepository;

    @Override
    public List<HandoverableAction> findHandoverableActions(Long memberId, HandoverType type) {
        boolean includeDoneActions = type == HandoverType.OFFBOARDING;
        return toHandoverableActions(actionRepository.findHandoverablePersonalActions(memberId, includeDoneActions));
    }

    @Override
    @Transactional
    public void reassign(Long actionId, Long fromMemberId, Long toMemberId) {
        Action action = actionRepository.findByIdForUpdate(actionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 액션입니다: " + actionId));

        if (!fromMemberId.equals(action.getAssigneeMemberId())) {
            throw new IllegalStateException("담당자가 일치하지 않는 액션입니다: " + actionId);
        }

        action.reassignTo(toMemberId);
        actionRepository.save(action);
    }

    @Override
    @Transactional
    public void rollbackReassignment(Long actionId, Long fromMemberId, Long toMemberId) {
        reassign(actionId, fromMemberId, toMemberId);
    }

    @Override
    public List<HandoverableAction> findHandoverableActions(Long memberId) {
        return toHandoverableActions(actionRepository.findHandoverablePersonalActions(memberId, true));
    }

    @Override
    public List<TeamActionForDeparture> findTeamActionsForDeparture(Long memberId) {
        return actionRepository.findTeamActionsByLeaderMemberId(memberId).stream()
                .map(action -> new TeamActionForDeparture(
                        action.getId(),
                        action.getTitle(),
                        action.getProjectId(),
                        action.getSourceMeetingId(),
                        action.getStatus().name(),
                        action.getTeamId()
                ))
                .toList();
    }

    private List<HandoverableAction> toHandoverableActions(List<Action> actions) {
        if (actions.isEmpty()) {
            return List.of();
        }

        Map<Long, String> projectTagsById = springDataProjectReferenceRepository.findAllById(
                actions.stream().map(Action::getProjectId).distinct().toList()
        ).stream().collect(Collectors.toMap(ProjectReferenceEntity::getId, ProjectReferenceEntity::getTag));

        Map<Long, String> meetingTitlesById = actionMeetingReferenceRepository.findAllById(
                actions.stream().map(Action::getSourceMeetingId).filter(Objects::nonNull).distinct().toList()
        ).stream().collect(Collectors.toMap(ActionMeetingReferenceEntity::getId, ActionMeetingReferenceEntity::getTitle));

        return actions.stream()
                .map(action -> new HandoverableAction(
                        action.getId(),
                        action.getTitle(),
                        projectTagsById.get(action.getProjectId()),
                        action.getProjectId(),
                        action.getActionType().name(),
                        action.getStatus().name(),
                        action.getDueDate(),
                        // E(인수인계) 요청 필드: 상세 타임라인 막대 시작점(액션 생성시각). @Getter 자동 생성.
                        action.getCreatedAt(),
                        action.getSourceMeetingId(),
                        action.getSourceMeetingId() == null ? null : meetingTitlesById.get(action.getSourceMeetingId()),
                        action.getDescription()
                ))
                .toList();
    }
}
