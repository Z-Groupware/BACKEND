package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.module06.backend.action.application.port.ActionQueryPort;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.project.domain.model.ProjectStatus;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 ActionRepository 계약을 JPA로 구현하는 어댑터(의존성 역전의 실행 지점).
    책임 두 가지 — Spring Data 호출 위임, 그리고 ActionJpaEntity ↔ Action 변환.
    착수한 슬라이스에 필요한 메서드만 구현해 나간다.

    saveAll은 AI 분배(ActionDistributionPort)의 벌크 생성용이다. Spring Data의 saveAll이
    입력 iteration 순서대로 결과를 돌려주므로 그 순서를 그대로 흘려보낸다 — 호출자가 채번된
    id를 원본 분배 입력과 인덱스로 짝짓기 때문에 여기서 정렬을 바꾸면 안 된다.

    findHandoverablePersonalActions·findTeamActionsByLeaderMemberId는 원래 JPQL로 다른
    엔티티와 직접 조인했으나 CI Gate 1(QUERY_002, 신규 @Query 금지)에 걸려 2단계 파생 쿼리로
    바꿨다 — 완료된 프로젝트 제외, 팀장 소속 판별을 이 어댑터가 자바 레벨에서 처리한다
    (2026-08-06).

    연결된 클래스
    - ActionRepository                        : 구현하는 도메인 계약
    - SpringDataActionRepository               : action 조회 위임 대상
    - SpringDataProjectReferenceRepository     : 완료된 프로젝트 제외 필터용
    - SpringDataActionTeamReferenceRepository  : 팀장 소속 팀 조회용
    - ActionJpaEntity                          : 변환 대상 엔티티
    - Action                                   : 변환 결과 도메인 모델
*/
@Component
@RequiredArgsConstructor
public class ActionPersistenceAdapter implements ActionRepository, ActionQueryPort {

    private final SpringDataActionRepository springDataActionRepository;
    private final SpringDataProjectReferenceRepository springDataProjectReferenceRepository;
    private final SpringDataActionTeamReferenceRepository springDataActionTeamReferenceRepository;

    @Override
    public Action save(Action action) {
        return toDomain(springDataActionRepository.save(toEntity(action)));
    }

    @Override
    public List<Action> saveAll(List<Action> actions) {
        if (actions.isEmpty()) {
            return List.of();
        }

        return springDataActionRepository.saveAll(actions.stream().map(this::toEntity).toList()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Action> findById(Long id) {
        return springDataActionRepository.findById(id).map(this::toDomain);
    }

    /* id로 지운다 — 도메인 객체를 엔티티로 되돌려 지우면 detached 인스턴스를 merge한 뒤
       삭제하게 되어, 그 사이 다른 트랜잭션이 고친 값이 되살아난다(RVW-04, 2026-08-07). */
    @Override
    public void delete(Action action) {
        springDataActionRepository.deleteById(action.getId());
    }

    @Override
    public List<Action> findHandoverablePersonalActions(Long memberId, boolean includeDoneActions) {
        List<ActionJpaEntity> candidates =
                springDataActionRepository.findAllByActionTypeAndAssigneeMemberId(ActionType.PERSONAL, memberId);

        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, ProjectStatus> projectStatusById = springDataProjectReferenceRepository.findAllById(
                candidates.stream().map(ActionJpaEntity::getProjectId).distinct().toList()
        ).stream().collect(Collectors.toMap(ProjectReferenceEntity::getId, ProjectReferenceEntity::getStatus));

        return candidates.stream()
                .filter(a -> projectStatusById.get(a.getProjectId()) != ProjectStatus.DONE)
                .filter(a -> includeDoneActions || a.getStatus() != ActionStatus.DONE)
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Action> findTeamActionsByLeaderMemberId(Long leaderMemberId) {
        List<Long> teamIds = springDataActionTeamReferenceRepository.findAllByLeaderMemberId(leaderMemberId).stream()
                .map(ActionTeamReferenceEntity::getId)
                .toList();

        if (teamIds.isEmpty()) {
            return List.of();
        }

        return springDataActionRepository.findAllByActionTypeAndTeamIdIn(ActionType.TEAM, teamIds).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TeamActionSummary> findTeamActionsByProjectId(Long projectId) {
        List<ActionJpaEntity> teamActions =
                springDataActionRepository.findAllByActionTypeAndProjectId(ActionType.TEAM, projectId);

        if (teamActions.isEmpty()) {
            return List.of();
        }

        Map<Long, String> teamNameById = springDataActionTeamReferenceRepository.findAllById(
                teamActions.stream().map(ActionJpaEntity::getTeamId).distinct().toList()
        ).stream().collect(Collectors.toMap(ActionTeamReferenceEntity::getId, ActionTeamReferenceEntity::getName));

        return teamActions.stream()
                .map(entity -> new TeamActionSummary(
                        entity.getId(),
                        entity.getTitle(),
                        entity.getTeamId(),
                        teamNameById.get(entity.getTeamId()),
                        entity.getStatus(),
                        entity.getDueDate()
                ))
                .toList();
    }

    private ActionJpaEntity toEntity(Action action) {
        return ActionJpaEntity.builder()
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
                .dueDateDefaulted(action.isDueDateDefaulted())
                .reviewStatus(action.getReviewStatus())
                .assigneeSource(action.getAssigneeSource())
                .evidenceTranscriptId(action.getEvidenceTranscriptId())
                .gateSignals(action.getGateSignals())
                .isManual(action.isManual())
                .confirmedAt(action.getConfirmedAt())
                .build();
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
                entity.isDueDateDefaulted(),
                entity.getReviewStatus(),
                entity.getAssigneeSource(),
                entity.getEvidenceTranscriptId(),
                entity.getGateSignals(),
                entity.isManual(),
                entity.getConfirmedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
