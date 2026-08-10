package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.module06.backend.action.application.port.ActionQueryPort;
import com.module06.backend.action.application.port.MeetingActionQueryPort;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
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

    MeetingActionQueryPort는 meeting(D)이 부르는 인바운드 포트다(2026-08-08). 마이페이지
    확정 대기 목록 배치조회는 IN 절이 무한정 커지지 않게 200건씩 내부적으로 청킹한다 —
    이건 이 어댑터만의 관심사이지 계약이 아니라 호출자가 크기를 맞출 필요는 없다(2026-08-08,
    모성진 확인 후 정리 — D도 같은 크기로 청킹하고 있어 계약처럼 보였지만 착오였다).
    COUNT GROUP BY가 Gate 1에 막혀 프로젝션으로 행을 읽어 자바에서 집계한다.

    연결된 클래스
    - ActionRepository                        : 구현하는 도메인 계약
    - MeetingActionQueryPort                  : 구현하는 인바운드 포트 (meeting(D) 호출)
    - SpringDataActionRepository               : action 조회 위임 대상
    - SpringDataProjectReferenceRepository     : 완료된 프로젝트 제외 필터용
    - SpringDataActionTeamReferenceRepository  : 팀장 소속 팀 조회용
    - ActionJpaEntity                          : 변환 대상 엔티티
    - Action                                   : 변환 결과 도메인 모델
*/
@Component
@RequiredArgsConstructor
public class ActionPersistenceAdapter implements ActionRepository, ActionQueryPort, MeetingActionQueryPort {

    // 이 어댑터 내부 청킹 크기 — 계약이 아니다(2026-08-08 정리). 호출자는 신경 쓸 필요 없다.
    private static final int MEETING_ID_BATCH_SIZE = 200;

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

    @Override
    public Optional<Action> findByIdForUpdate(Long id) {
        return springDataActionRepository.findWithLockById(id).map(this::toDomain);
    }

    // 내 액션 목록 — PERSONAL만 담당자 개념이 있다.
    @Override
    public List<Action> findAllByAssigneeMemberId(Long assigneeMemberId) {
        return springDataActionRepository.findAllByActionTypeAndAssigneeMemberId(ActionType.PERSONAL, assigneeMemberId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // 배치 조회 — 빈 id 목록이면 IN 절 쿼리 자체를 건너뛴다.
    @Override
    public List<Action> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return springDataActionRepository.findAllById(ids).stream()
                .map(this::toDomain)
                .toList();
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

    // FR-AC-06 — 팀 액션 목록. 기존 findAllByActionTypeAndTeamIdIn을 단일 teamId로 재사용한다.
    @Override
    public List<Action> findAllByTeamId(Long teamId) {
        return springDataActionRepository.findAllByActionTypeAndTeamIdIn(ActionType.TEAM, List.of(teamId)).stream()
                .map(this::toDomain)
                .toList();
    }

    // FR-AC-08 — 팀 액션 타임라인. companyId·PERSONAL 조건을 조회 자체에 넣어 다른 회사 행이나
    // TEAM 액션이 섞여 들어올 여지를 원천 차단한다.
    @Override
    public List<Action> findAllByParentActionId(Long companyId, Long parentActionId) {
        return springDataActionRepository
                .findAllByActionTypeAndCompanyIdAndParentActionId(ActionType.PERSONAL, companyId, parentActionId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // FR-AC-09 — 회의별 액션 조회. TEAM·PERSONAL 조건 없이 그대로 옮겨 담는다.
    @Override
    public List<Action> findAllByCompanyIdAndSourceMeetingId(Long companyId, Long sourceMeetingId) {
        return springDataActionRepository.findAllByCompanyIdAndSourceMeetingId(companyId, sourceMeetingId).stream()
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

    @Override
    public List<ActionQueryPort.ProjectActionCount> countActionsByProjectIds(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SpringDataActionRepository.ProjectActionProjection>> byProjectId =
                springDataActionRepository.findAllByProjectIdIn(projectIds).stream()
                        .collect(Collectors.groupingBy(SpringDataActionRepository.ProjectActionProjection::getProjectId));

        return byProjectId.entrySet().stream()
                .map(entry -> new ActionQueryPort.ProjectActionCount(
                        entry.getKey(),
                        entry.getValue().size(),
                        (int) entry.getValue().stream()
                                .filter(projection -> projection.getStatus() == ActionStatus.DONE)
                                .count()
                ))
                .toList();
    }

    // MEET-01 회의 예약 시 relatedActionId 검증용 — 단순 위임.
    @Override
    public boolean existsAction(Long companyId, Long actionId) {
        return springDataActionRepository.existsByCompanyIdAndId(companyId, actionId);
    }

    // 마이페이지 확정 대기 목록 — 200건씩 청킹해 조회하고 회의별로 미분배 건수를 집계한다.
    @Override
    public List<MeetingUndispatchedActions> findMeetingsWithUndispatchedActions(
            Long companyId, List<Long> sourceMeetingIds) {
        if (companyId == null || sourceMeetingIds == null || sourceMeetingIds.isEmpty()) {
            return List.of();
        }

        // 입력에 중복 meetingId가 있으면 서로 다른 청크에 나뉘어 들어갈 수 있고, 그러면 같은
        // 회의의 실제 DB 행이 두 청크의 쿼리에서 각각 조회돼 집계에서 두 번 세어진다(코드래빗
        // 지적, PR #229). distinct()로 청킹 전에 제거한다.
        return chunk(sourceMeetingIds.stream().distinct().toList(), MEETING_ID_BATCH_SIZE).stream()
                .flatMap(chunk -> springDataActionRepository
                        .findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(
                                companyId, chunk, ActionReviewStatus.REJECTED)
                        .stream())
                .collect(Collectors.groupingBy(
                        SpringDataActionRepository.UndispatchedProjection::getSourceMeetingId, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new MeetingUndispatchedActions(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<List<Long>> chunk(List<Long> ids, int size) {
        return IntStream.range(0, (ids.size() + size - 1) / size)
                .mapToObj(i -> ids.subList(i * size, Math.min(ids.size(), (i + 1) * size)))
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
                .isDone(action.isDone())
                .startDate(action.getStartDate())
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
                entity.isDone(),
                entity.getStartDate(),
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
