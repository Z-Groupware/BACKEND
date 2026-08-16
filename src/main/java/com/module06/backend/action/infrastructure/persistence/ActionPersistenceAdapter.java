package com.module06.backend.action.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.module06.backend.action.application.port.ActionQueryPort;
import com.module06.backend.action.application.port.MeetingActionQueryPort;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.project.domain.model.ProjectStatus;

import jakarta.persistence.criteria.Predicate;
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

    // 내 액션 목록 — PERSONAL만 담당자 개념이 있다. 캘린더가 월간 집계에 전건을 쓰므로 그대로 둔다.
    @Override
    public List<Action> findAllByAssigneeMemberId(Long assigneeMemberId) {
        return springDataActionRepository.findAllByActionTypeAndAssigneeMemberId(ActionType.PERSONAL, assigneeMemberId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // 2026-08-10 페이지네이션+필터+정렬 도입(이홍근 요청) — 목록 화면 전용.
    @Override
    public List<Action> findAllByAssigneeMemberId(
            Long assigneeMemberId, ActionStatus status, Boolean overdue, String sort, String order, int page, int size) {
        Specification<ActionJpaEntity> specification =
                buildActionSpecification(ActionType.PERSONAL, assigneeMemberId, null, status, overdue);
        PageRequest pageRequest = PageRequest.of(page, size, buildActionSort(sort, order));

        return springDataActionRepository.findAll(specification, pageRequest).getContent().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByAssigneeMemberId(Long assigneeMemberId, ActionStatus status, Boolean overdue) {
        return springDataActionRepository.count(
                buildActionSpecification(ActionType.PERSONAL, assigneeMemberId, null, status, overdue));
    }

    // content 쿼리와 count 쿼리가 항상 같은 조건을 쓰도록 이 메서드 하나로 통일한다(개인 목록은
    // teamId=null, 팀 목록은 assigneeMemberId=null로 호출) — totalElements가 필터링 전 기준이면
    // 화면이 거짓말을 하게 된다. overdue는 저장값이 아니라 status=IN_PROGRESS AND dueDate<오늘로
    // 매번 계산하는 파생 조건이다(status처럼 컬럼이 따로 없음, 2026-08-07 재설계와 동일 정의).
    private Specification<ActionJpaEntity> buildActionSpecification(
            ActionType actionType, Long assigneeMemberId, Long teamId, ActionStatus status, Boolean overdue) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("actionType"), actionType));
            if (assigneeMemberId != null) {
                predicates.add(cb.equal(root.get("assigneeMemberId"), assigneeMemberId));
            }
            if (teamId != null) {
                predicates.add(cb.equal(root.get("teamId"), teamId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (overdue != null) {
                Predicate isOverdue = cb.and(
                        cb.equal(root.get("status"), ActionStatus.IN_PROGRESS),
                        cb.lessThan(root.get("dueDate"), java.time.LocalDate.now()));
                predicates.add(overdue ? isOverdue : cb.not(isOverdue));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 정렬 화이트리스트 — dueDate·createdAt만 허용, 그 외는 기본 정렬로 대체(400 대신).
    private Sort buildActionSort(String sort, String order) {
        String field = switch (sort == null ? "" : sort) {
            case "dueDate" -> "dueDate";
            case "createdAt" -> "createdAt";
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // id를 보조 정렬키로 덧붙인다 — dueDate·createdAt만으로는 같은 값을 가진 행이 여러 개일 때
        // DB가 순서를 보장 안 해서, 페이지 경계에서 같은 행이 두 번 나오거나 아예 빠질 수 있다
        // (CodeRabbit 지적, PR #305).
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
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

    // 상위 팀 액션명 스냅샷 배치 조회 — actionType(TEAM)·companyId로 스코프해 회사·종류 불변식을
    // 조회 자체에서 보장한다(CodeRabbit PR #382 지적).
    @Override
    public List<Action> findTeamActionsByIds(Long companyId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return springDataActionRepository
                .findAllByActionTypeAndCompanyIdAndIdIn(ActionType.TEAM, companyId, ids).stream()
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
    // 2026-08-10 페이지네이션 도입(이홍근 요청).
    @Override
    public List<Action> findAllByTeamId(Long teamId, ActionStatus status, String sort, String order, int page, int size) {
        Specification<ActionJpaEntity> specification =
                buildActionSpecification(ActionType.TEAM, null, teamId, status, null);
        PageRequest pageRequest = PageRequest.of(page, size, buildActionSort(sort, order));

        return springDataActionRepository.findAll(specification, pageRequest).getContent().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByTeamId(Long teamId, ActionStatus status) {
        return springDataActionRepository.count(
                buildActionSpecification(ActionType.TEAM, null, teamId, status, null));
    }

    // 2026-08-11 — 팀 대시보드 KPI "팀원 액션" 카드. CodeRabbit(#354) 지적 반영 —
    // ActionTypeShapePolicy.checkTeamShape상 PERSONAL 액션은 teamId를 가질 수 없어(항상 null),
    // PERSONAL의 teamId를 직접 필터링하던 이전 구현(countByTeamIdAndActionType)은 매치가
    // 절대 안 생겨 카운트가 항상 0이었다. "팀 소속 개인 액션"을 이 팀의 TEAM 액션을 부모로
    // 둔 PERSONAL 액션으로 다시 정의해, 먼저 이 팀의 TEAM 액션 id를 모으고(findTeamActionsByLeaderMemberId가
    // 쓰는 것과 같은 전건 조회) parentActionId IN 조건으로 PERSONAL을 센다.
    @Override
    public long countTeamMemberActionsByTeamId(Long teamId) {
        List<Long> teamActionIds = springDataActionRepository
                .findAllByActionTypeAndTeamIdIn(ActionType.TEAM, List.of(teamId)).stream()
                .map(ActionJpaEntity::getId)
                .toList();
        if (teamActionIds.isEmpty()) {
            return 0;
        }

        Specification<ActionJpaEntity> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("actionType"), ActionType.PERSONAL),
                root.get("parentActionId").in(teamActionIds));

        return springDataActionRepository.count(specification);
    }

    // 2026-08-11 — 팀원 현황 "담당 액션 수" 배치 집계. countActionsByProjectIds와 동일한
    // 프로젝션+자바 집계 패턴(Gate 1 QUERY_002가 COUNT GROUP BY용 신규 @Query를 막는다).
    @Override
    public List<AssigneeActionCount> countActionsByAssigneeMemberIds(List<Long> assigneeMemberIds) {
        if (assigneeMemberIds.isEmpty()) {
            return List.of();
        }

        return springDataActionRepository
                .findAllByActionTypeAndAssigneeMemberIdIn(ActionType.PERSONAL, assigneeMemberIds).stream()
                .collect(Collectors.groupingBy(
                        SpringDataActionRepository.AssigneeActionProjection::getAssigneeMemberId, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new AssigneeActionCount(entry.getKey(), entry.getValue()))
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

    // PERSONAL 액션만 센다(WORKFLOW §1, 2026-08-16 확정) — 팀 액션은 하위 개인 액션 완료로 파생되는
    // 거울이라(reconcileTeamActionStatus) 함께 세면 같은 완료가 분자·분모에 두 번 잡힌다.
    //
    // 그래서 하위 개인 액션이 아직 없는 팀 액션만 있는 프로젝트는 집계 결과가 아예 없고,
    // 호출부에서 0/0 → 진척율 0%로 떨어진다. 100%가 아니라 0%인 것이 의도한 값이다.
    @Override
    public List<ActionQueryPort.ProjectActionCount> countActionsByProjectIds(Long companyId, List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SpringDataActionRepository.ProjectActionProjection>> byProjectId =
                springDataActionRepository
                        .findAllByActionTypeAndCompanyIdAndProjectIdIn(ActionType.PERSONAL, companyId, projectIds)
                        .stream()
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

    // 2026-08-11, 이슈 #355 — 팀 액션 목록 하위 개인 액션 진척 배치 집계. countActionsByProjectIds와
    // 동일 패턴(프로젝션+자바 집계).
    @Override
    public List<ChildActionProgress> countChildActionProgressByParentActionIds(Long companyId, List<Long> parentActionIds) {
        if (parentActionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SpringDataActionRepository.ChildActionProgressProjection>> byParentActionId =
                springDataActionRepository
                        .findAllByActionTypeAndCompanyIdAndParentActionIdIn(ActionType.PERSONAL, companyId, parentActionIds)
                        .stream()
                        .collect(Collectors.groupingBy(SpringDataActionRepository.ChildActionProgressProjection::getParentActionId));

        return byParentActionId.entrySet().stream()
                .map(entry -> new ChildActionProgress(
                        entry.getKey(),
                        entry.getValue().size(),
                        (int) entry.getValue().stream()
                                .filter(projection -> projection.getStatus() == ActionStatus.DONE)
                                .count()
                ))
                .toList();
    }

    // MEET-01 회의 예약 시 relatedActionId 검증용 — 단순 위임.
    // findActionTeamReference로 대체 예정(위 인터페이스 주석 참고) — D 마이그레이션 전까지 유지.
    @Override
    public boolean existsAction(Long companyId, Long actionId) {
        return springDataActionRepository.existsByCompanyIdAndId(companyId, actionId);
    }

    // 2026-08-12, 모성진(D) 요청 — 회의–액션 팀 일치 검증용 단건 조회.
    @Override
    public Optional<MeetingActionQueryPort.ActionTeamReference> findActionTeamReference(Long companyId, Long actionId) {
        return springDataActionRepository.findByCompanyIdAndId(companyId, actionId)
                .map(projection -> new MeetingActionQueryPort.ActionTeamReference(
                        projection.getTeamId(), projection.getActionType()));
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

    // 2026-08-10, 모성진(D) 요청 — 회의별 전체 액션 건수(분배·검토 상태 무관). 위
    // findMeetingsWithUndispatchedActions와 같은 청킹+groupingBy 패턴, 조건만 뺐다.
    @Override
    public List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> sourceMeetingIds) {
        if (companyId == null || sourceMeetingIds == null || sourceMeetingIds.isEmpty()) {
            return List.of();
        }

        return chunk(sourceMeetingIds.stream().distinct().toList(), MEETING_ID_BATCH_SIZE).stream()
                .flatMap(chunk -> springDataActionRepository
                        .findAllByCompanyIdAndSourceMeetingIdIn(companyId, chunk)
                        .stream())
                .collect(Collectors.groupingBy(
                        SpringDataActionRepository.UndispatchedProjection::getSourceMeetingId, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new MeetingActionCount(entry.getKey(), entry.getValue()))
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
                .plannedStartDate(action.getPlannedStartDate())
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
                entity.getPlannedStartDate(),
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
