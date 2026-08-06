package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.repository.ActionRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 ActionRepository 계약을 JPA로 구현하는 어댑터(의존성 역전의 실행 지점).
    책임 두 가지 — Spring Data 호출 위임, 그리고 ActionJpaEntity ↔ Action 변환.
    이번 슬라이스(ActionReassignPort 배선)에 필요한 메서드만 우선 구현.

    연결된 클래스
    - ActionRepository           : 구현하는 도메인 계약
    - SpringDataActionRepository : 실제 쿼리 위임 대상
    - ActionJpaEntity            : 변환 대상 엔티티
    - Action                     : 변환 결과 도메인 모델
*/
@Component
@RequiredArgsConstructor
public class ActionPersistenceAdapter implements ActionRepository {

    private final SpringDataActionRepository springDataActionRepository;

    @Override
    public Action save(Action action) {
        ActionJpaEntity entity = ActionJpaEntity.builder()
                .id(action.getId())
                .companyId(action.getCompanyId())
                .projectId(action.getProjectId())
                .parentActionId(action.getParentActionId())
                .sourceMeetingId(action.getSourceMeetingId())
                .teamId(action.getTeamId())
                .assigneeMemberId(action.getAssigneeMemberId())
                .actionType(action.getActionType())
                .title(action.getTitle())
                .description(action.getDescription())
                .status(action.getStatus())
                .dueDate(action.getDueDate())
                .needsReview(action.isNeedsReview())
                .confirmedAt(action.getConfirmedAt())
                .build();

        return toDomain(springDataActionRepository.save(entity));
    }

    @Override
    public Optional<Action> findById(Long id) {
        return springDataActionRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Action> findHandoverablePersonalActions(Long memberId, boolean includeDoneActions) {
        return springDataActionRepository.findHandoverablePersonalActions(memberId, includeDoneActions).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Action> findTeamActionsByLeaderMemberId(Long leaderMemberId) {
        return springDataActionRepository.findTeamActionsByLeaderMemberId(leaderMemberId).stream()
                .map(this::toDomain)
                .toList();
    }

    private Action toDomain(ActionJpaEntity entity) {
        return Action.reconstitute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getProjectId(),
                entity.getParentActionId(),
                entity.getSourceMeetingId(),
                entity.getTeamId(),
                entity.getAssigneeMemberId(),
                entity.getActionType(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getDueDate(),
                entity.isNeedsReview(),
                entity.getConfirmedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
