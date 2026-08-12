package com.module06.backend.action.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.command.BulkUpdateActionStatusCommand;
import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.policy.PersonalActionAssigneeOnlyPolicy;
import com.module06.backend.action.application.usecase.BulkUpdateActionStatusUseCase;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.policy.ActionTypeShapePolicy;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MemberReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MeetingReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.SubTeamReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.in.MeetingQueryPort;
import com.module06.backend.project.application.port.ProjectQueryPort;

import lombok.RequiredArgsConstructor;

/* comment.
    개인 액션 리소스(FR-AC-01,02,03,09)를 다루는 단일 구현체. 쓰기·읽기 트랜잭션 경계는
    메서드별로 갈린다(create·updateStatus·bulkUpdateStatus는 쓰기, getMy·getDetail·
    getByMeeting은 읽기 전용).
    FR-AC-04(리뷰확정)는 여기 없다 — review(A)가 RVW-02를 자체 구현했고(capture.
    ApplyReviewDecisionService), C는 그쪽이 선언한 ActionReviewApplyPort를
    ActionReviewApplyAdapter(infrastructure.adapter)로 받는 것으로 끝난다. C가 별도
    REST 엔드포인트를 노출할 이유가 없어 ReviewActionUseCase·Command·Request는
    2026-08-07 삭제(죽은 스켈레톤이었음).
    getActionsByMeeting(FR-AC-09)은 회의 하나에서 개인·팀 액션이 actionType으로 섞여 나오는
    조회라 TeamActionService로 뺄지 애매했는데, 조회 주체가 "회의"지 "팀"이 아니고 개인 액션도
    함께 나오므로 여기 남겼다 — 팀 전용 조회는 전부 TeamActionService로 분리했다.
    UseCase 인터페이스는 엔드포인트 1:1(포트 경계)로 유지하되, 구현체는 같은 리소스를
    다루는 것끼리 이 클래스 하나로 묶었다 — 08/04 팀 협의(윤종호)로 project 도메인에 이어 action에도
    동일 원칙 적용.

    create(FR-AC-01 예외 경로)는 project(C)가 선언한 ProjectQueryPort로 프로젝트가 같은 회사
    소속이고 활성 상태인지 확인하고(meeting(D)이 회의 개설 시 쓰는 것과 동일한 검증), teamId·
    assigneeMemberId도 ActionReferenceRepository로 같은 회사 소속인지 검증한다 — 아니면
    다른 회사 팀·구성원에 액션을 붙이는 IDOR이 된다(2026-08-06 CodeRabbit PR #151 지적).

    연결된 클래스
    - CreateActionUseCase · GetMyActionsUseCase · GetActionDetailUseCase ·
      BulkUpdateActionStatusUseCase · GetActionsByMeetingUseCase : 구현하는 계약
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사
    - ActionTypeShapePolicy            : 수동 생성 시 종류별 필드 조합 검증 (domain.policy)
    - ActionRepository                 : 저장·조회
    - ActionReferenceRepository        : teamId·assigneeMemberId 회사 소속 검증
    - ProjectQueryPort                 : project(C)가 선언한 프로젝트 존재·활성 검증 포트
*/
@Service
@RequiredArgsConstructor
public class ActionService implements
        CreateActionUseCase,
        GetMyActionsUseCase,
        GetActionDetailUseCase,
        BulkUpdateActionStatusUseCase,
        GetActionsByMeetingUseCase {

    // 상태 없는 순수 규칙이라 빈으로 띄우지 않는다 — domain 계층에 스프링 애노테이션을 넣지 않기 위함(절대규칙 5항).
    private static final ActionTypeShapePolicy ACTION_TYPE_SHAPE_POLICY = new ActionTypeShapePolicy();
    private static final PersonalActionAssigneeOnlyPolicy ASSIGNEE_ONLY_POLICY = new PersonalActionAssigneeOnlyPolicy();

    private final ActionRepository actionRepository;
    private final ActionReferenceRepository actionReferenceRepository;
    private final ProjectQueryPort projectQueryPort;
    private final MeetingQueryPort meetingQueryPort;

    @Override
    @Transactional
    public Action create(CreateActionCommand command) {
        if (!projectQueryPort.existsActiveProject(command.companyId(), command.projectId())) {
            throw new BusinessException(ActionErrorCode.ACTION_PROJECT_NOT_FOUND);
        }

        ACTION_TYPE_SHAPE_POLICY.checkManual(command.actionType(), command.teamId(), command.assigneeMemberId());

        if (command.actionType() == ActionType.TEAM) {
            if (!actionReferenceRepository.existsTeamInCompany(command.teamId(), command.companyId())) {
                throw new BusinessException(ActionErrorCode.ACTION_TEAM_NOT_FOUND);
            }
        } else if (!actionReferenceRepository.existsMemberInCompany(command.assigneeMemberId(), command.companyId())) {
            throw new BusinessException(ActionErrorCode.ACTION_ASSIGNEE_NOT_FOUND);
        }

        Action action = Action.createManual(
                command.companyId(),
                command.projectId(),
                command.teamId(),
                command.assigneeMemberId(),
                command.actionType(),
                command.title(),
                command.description(),
                command.dueDate()
        );

        return actionRepository.save(action);
    }

    // FR-AC-02 목록 — 기본은 호출자 본인 소유 PERSONAL 액션, 표시값은 배치조회로 조인한다.
    // 2026-08-10 페이지네이션 도입(이홍근 요청) — totalElements는 요청한 page와 무관하게 항상
    // 전체 건수라 비어있는 페이지에서도 먼저 구해둔다.
    // 2026-08-11 — targetMemberId가 있으면(팀원 관리 화면) 호출자가 LEADER인지, 대상이 같은 팀
    // 소속인지 검증 후 그 팀원의 목록으로 스코프를 바꾼다. IDOR 방지 — 순서 중요(리더 자격 먼저,
    // 그다음 팀 소속 확인).
    @Override
    @Transactional(readOnly = true)
    public GetMyActionsUseCase.ActionListResult getMyActions(
            Long requesterId, String requesterAuthority, Long requesterTeamId, Long targetMemberId,
            ActionStatus status, Boolean overdue, String sort, String order, int page, int size) {
        Long assigneeMemberId = requesterId;
        if (targetMemberId != null) {
            if (!"LEADER".equals(requesterAuthority)) {
                throw new BusinessException(ActionErrorCode.NOT_TEAM_LEADER);
            }
            // requesterTeamId가 null이면 existsMemberInTeam(targetId, null)이 Spring Data JPA의
            // "null 파라미터 → IS NULL" 변환 때문에 team_id가 똑같이 NULL인 팀 무소속 대상과
            // 우연히 매치해버릴 수 있다(CodeRabbit 지적, 2026-08-11) — 스코프로 삼을 팀 자체가
            // 없으니 매칭 여부와 무관하게 여기서 막는다.
            if (requesterTeamId == null || !actionReferenceRepository.existsMemberInTeam(targetMemberId, requesterTeamId)) {
                throw new BusinessException(ActionErrorCode.ACTION_ASSIGNEE_OUT_OF_TEAM_SCOPE);
            }
            assigneeMemberId = targetMemberId;
        }

        long totalElements = actionRepository.countByAssigneeMemberId(assigneeMemberId, status, overdue);
        List<Action> actions = actionRepository.findAllByAssigneeMemberId(
                assigneeMemberId, status, overdue, sort, order, page, size);
        if (actions.isEmpty()) {
            return new GetMyActionsUseCase.ActionListResult(List.of(), totalElements);
        }

        // 담당자는 호출자 본인 한 명뿐이라 단건 조회로 충분하다.
        String assigneeName = actionReferenceRepository.findMemberReferences(List.of(assigneeMemberId)).stream()
                .findFirst().map(MemberReference::name).orElse(null);

        List<ProjectReference> projectReferences =
                actionReferenceRepository.findProjectReferences(distinct(actions, Action::getProjectId));
        Map<Long, String> projectTagById = toDisplayMap(projectReferences, ProjectReference::projectId, ProjectReference::tag);
        Map<Long, String> projectNameById = toDisplayMap(projectReferences, ProjectReference::projectId, ProjectReference::name);
        Map<Long, String> teamNameById = toDisplayMap(
                actionReferenceRepository.findTeamReferences(distinctNonNull(actions, Action::getTeamId)),
                TeamReference::teamId, TeamReference::name);
        Map<Long, String> meetingTitleById = toDisplayMap(
                actionReferenceRepository.findMeetingReferences(distinctNonNull(actions, Action::getSourceMeetingId)),
                MeetingReference::meetingId, MeetingReference::title);
        Map<Long, String> parentTitleById = actionRepository.findAllByIds(distinctNonNull(actions, Action::getParentActionId))
                .stream().collect(Collectors.toMap(Action::getId, Action::getTitle));

        List<GetMyActionsUseCase.ActionListItem> items = actions.stream()
                .map(action -> new GetMyActionsUseCase.ActionListItem(
                        action,
                        assigneeName,
                        projectTagById.get(action.getProjectId()),
                        projectNameById.get(action.getProjectId()),
                        action.getTeamId() == null ? null : teamNameById.get(action.getTeamId()),
                        action.getSourceMeetingId() == null ? null : meetingTitleById.get(action.getSourceMeetingId()),
                        action.getParentActionId() == null ? null : parentTitleById.get(action.getParentActionId())
                ))
                .toList();

        return new GetMyActionsUseCase.ActionListResult(items, totalElements);
    }

    // FR-AC-02 상세 — 전 구성원 공개, 회사 스코프만 다시 확인한다(IDOR 방지, #100과 동일 판단).
    @Override
    @Transactional(readOnly = true)
    public GetActionDetailUseCase.ActionDetail getActionDetail(Long companyId, Long actionId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));

        if (!companyId.equals(action.getCompanyId())) {
            throw new BusinessException(ActionErrorCode.ACTION_NOT_FOUND);
        }

        MemberReference assignee = action.getAssigneeMemberId() == null ? null
                : actionReferenceRepository.findMemberReferences(List.of(action.getAssigneeMemberId())).stream()
                        .findFirst().orElse(null);
        String assigneeName = assignee == null ? null : assignee.name();
        // "역할"(sub_team)은 team과 달리 리더 없는 순수 분류 태그라 미지정 담당자는 null(이홍근 확인, 2026-08-11).
        String assigneeRoleLabel = assignee == null || assignee.subTeamId() == null ? null
                : actionReferenceRepository.findSubTeamReferences(List.of(assignee.subTeamId())).stream()
                        .findFirst().map(SubTeamReference::name).orElse(null);

        ProjectReference project = actionReferenceRepository.findProjectReferences(List.of(action.getProjectId())).stream()
                .findFirst().orElse(null);

        String teamName = action.getTeamId() == null ? null
                : actionReferenceRepository.findTeamReferences(List.of(action.getTeamId())).stream()
                        .findFirst().map(TeamReference::name).orElse(null);

        MeetingReference sourceMeeting = action.getSourceMeetingId() == null ? null
                : actionReferenceRepository.findMeetingReferences(List.of(action.getSourceMeetingId())).stream()
                        .findFirst().orElse(null);
        String sourceMeetingTitle = sourceMeeting == null ? null : sourceMeeting.title();
        LocalDateTime sourceMeetingScheduledAt = sourceMeeting == null ? null : sourceMeeting.scheduledAt();

        // 상위 TEAM 액션 카드(팀명·마감일)용 — 부모는 항상 TEAM 액션이라 자신의 teamId를 그대로 쓴다.
        Action parentAction = action.getParentActionId() == null ? null
                : actionRepository.findById(action.getParentActionId()).orElse(null);
        String parentActionTitle = parentAction == null ? null : parentAction.getTitle();
        String parentActionTeamName = parentAction == null || parentAction.getTeamId() == null ? null
                : actionReferenceRepository.findTeamReferences(List.of(parentAction.getTeamId())).stream()
                        .findFirst().map(TeamReference::name).orElse(null);
        LocalDate parentActionDueDate = parentAction == null ? null : parentAction.getDueDate();

        return new GetActionDetailUseCase.ActionDetail(
                action, assigneeName, assigneeRoleLabel,
                project == null ? null : project.tag(),
                project == null ? null : project.name(),
                teamName, sourceMeetingTitle, sourceMeetingScheduledAt,
                parentActionTitle, parentActionTeamName, parentActionDueDate
        );
    }

    /*
     * FR-AC-03 보드 저장 — all-or-nothing. 2026-08-07 isDone 재설계(이홍근 확인) 이후로
     * 실제 허용되는 전환은 셋뿐이다: 할일→진행중(startDate=오늘), 진행중→완료(isDone=true),
     * 완료→진행중(isDone=false). 목표 칸은 요청 그대로 받되(API 모양 유지), 서버가 각 액션의
     * 현재 파생 상태를 다시 계산해서 그 전환이 가능한지 재검증한다 — 클라이언트가 보낸 값만
     * 믿으면 드래그 제약을 API 직접 호출로 우회할 수 있다.
     */
    @Override
    @Transactional
    public void bulkUpdateStatus(BulkUpdateActionStatusCommand command) {
        List<Long> actionIds = command.items().stream().map(BulkUpdateActionStatusCommand.Item::actionId).toList();
        Map<Long, Action> actionsById = actionRepository.findAllByIds(actionIds).stream()
                .collect(Collectors.toMap(Action::getId, Function.identity()));

        // 검증 단계 — 존재·담당자 본인·전환 가능 여부를 먼저 확인하고, 하나라도 걸리면 아무것도 바꾸지 않는다.
        for (BulkUpdateActionStatusCommand.Item item : command.items()) {
            Action action = actionsById.get(item.actionId());
            if (action == null) {
                throw new BusinessException(ActionErrorCode.ACTION_NOT_FOUND);
            }
            ASSIGNEE_ONLY_POLICY.check(action, command.requesterId());
            requireReachableTransition(action.getStatus(), item.status());
        }

        // 반영 단계 — 목표 칸에 따라 실제로 쓰는 필드가 다르다. 실제로 상태가 바뀐 PERSONAL 액션의
        // 부모(TEAM)는 이슈 #383 대상 — 이 요청이 끝나기 전에 같이 재평가한다.
        Set<Long> touchedParentIds = new LinkedHashSet<>();
        for (BulkUpdateActionStatusCommand.Item item : command.items()) {
            Action action = actionsById.get(item.actionId());
            ActionStatus before = action.getStatus();
            applyTransition(action, item.status());
            if (action.getStatus() != before && action.getParentActionId() != null) {
                touchedParentIds.add(action.getParentActionId());
            }
        }

        List<Action> toSave = new ArrayList<>(actionsById.values());
        for (Long parentActionId : touchedParentIds) {
            reconcileTeamActionStatus(parentActionId, actionsById).ifPresent(toSave::add);
        }

        actionRepository.saveAll(toSave);
    }

    /*
     * 이슈 #383 — TEAM 액션은 담당자가 없어(ActionTypeShapePolicy) 보드에서 직접 상태를 바꿀
     * 방법이 없다(PersonalActionAssigneeOnlyPolicy가 assigneeMemberId==null인 TEAM을 항상
     * 막는다). 그래서 하위 PERSONAL 액션들의 상태로부터 매번 다시 계산해서 반영한다 — 시스템이
     * 계산한 파생이라 ASSIGNEE_ONLY_POLICY를 거치지 않는다(거치면 항상 실패한다).
     *
     * 대칭 되돌리기 포함(홍근 확인, 2026-08-12): 하위 하나가 DONE에서 IN_PROGRESS로 reopen되면
     * TEAM도 자동으로 IN_PROGRESS로 돌아간다 — 개인 액션 자신의 "자동 착수는 못 되돌린다"
     * 규칙(requireReachableTransition의 case TODO -> false)과는 별개다. TEAM은 자기 상태를
     * 갖지 않고 하위 집계를 그대로 반영하는 거울이라, 하위가 되돌아가면 거울도 같이 되돌아가야
     * 값이 거짓말을 하지 않는다.
     *
     * CodeRabbit 지적(PR #384) — findAllByParentActionId는 DB를 다시 읽는다. 이 시점엔 아직
     * saveAll을 안 해서, 이번 요청에서 방금 메모리로만 바꾼 하위 상태가 그 조회에 안 잡힌다.
     * updatedActionsById(이번 요청에서 이미 전이 적용된 액션들)로 조회 결과를 덮어써서 채운다 —
     * 이번 요청 밖의 형제는 DB 값 그대로(맞는 값), 이번 요청에서 바뀐 형제는 최신 값으로 교체.
     */
    private Optional<Action> reconcileTeamActionStatus(Long parentActionId, Map<Long, Action> updatedActionsById) {
        Action parent = actionRepository.findById(parentActionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));
        List<Action> children = actionRepository.findAllByParentActionId(parent.getCompanyId(), parentActionId).stream()
                .map(child -> updatedActionsById.getOrDefault(child.getId(), child))
                .toList();
        ActionStatus target = deriveTeamStatusFromChildren(children);
        if (parent.getStatus() == target) {
            return Optional.empty();
        }
        applyDerivedTransition(parent, target);
        return Optional.of(parent);
    }

    // 하위 0개 = 영원히 TODO(의도된 엣지케이스). 전부 DONE만 DONE, 하나라도 TODO 아니면 IN_PROGRESS.
    private static ActionStatus deriveTeamStatusFromChildren(List<Action> children) {
        if (children.isEmpty()) {
            return ActionStatus.TODO;
        }
        if (children.stream().allMatch(child -> child.getStatus() == ActionStatus.DONE)) {
            return ActionStatus.DONE;
        }
        boolean anyStarted = children.stream().anyMatch(child -> child.getStatus() != ActionStatus.TODO);
        return anyStarted ? ActionStatus.IN_PROGRESS : ActionStatus.TODO;
    }

    // target=TODO는 도달 불가능하다 — 하위 PERSONAL이 IN_PROGRESS에서 TODO로 못 돌아가는 이상
    // (case TODO -> false) 이 자리에 절대 안 들어와야 한다. 들어오면 호출부 계산이 잘못된 것.
    private static void applyDerivedTransition(Action parent, ActionStatus target) {
        if (target == ActionStatus.IN_PROGRESS) {
            if (parent.getStatus() == ActionStatus.TODO) {
                parent.start();
            } else if (parent.getStatus() == ActionStatus.DONE) {
                parent.reopen();
            }
        } else if (target == ActionStatus.DONE) {
            // 리컨실을 한 번도 안 거친 채로 하위가 이미 전부 DONE인 엣지케이스 — 먼저 착수부터 찍는다.
            if (parent.getStatus() == ActionStatus.TODO) {
                parent.start();
            }
            parent.complete();
        } else {
            throw new IllegalStateException("팀 액션은 자동으로 할일 상태로 되돌아갈 수 없습니다.");
        }
    }

    // "할일"은 도달 불가능한 목표 칸이다(그리로 되돌리는 동작 자체가 화면에 없다).
    private static void requireReachableTransition(ActionStatus current, ActionStatus target) {
        boolean reachable = switch (target) {
            case TODO -> false;
            case IN_PROGRESS -> current == ActionStatus.TODO || current == ActionStatus.IN_PROGRESS || current == ActionStatus.DONE;
            case DONE -> current == ActionStatus.IN_PROGRESS || current == ActionStatus.DONE;
        };
        if (!reachable) {
            throw new BusinessException(ActionErrorCode.ACTION_INVALID_STATUS_TRANSITION);
        }
    }

    private static void applyTransition(Action action, ActionStatus target) {
        ActionStatus current = action.getStatus();
        if (current == target) {
            return; // 이미 목표 칸 — 그대로 재저장(all-or-nothing 저장 대상에는 남지만 값은 안 바뀐다).
        }
        if (target == ActionStatus.IN_PROGRESS) {
            if (current == ActionStatus.TODO) {
                action.start();
            } else {
                action.reopen(); // current == DONE, requireReachableTransition이 이미 보장
            }
        } else if (target == ActionStatus.DONE) {
            action.complete();
        }
    }

    // FR-AC-09 — 회의별 액션 조회. TEAM은 담당자, PERSONAL은 팀 개념이 없어 서로 null로 채워진다.
    //
    // 액션 조회 자체는 companyId로 스코프되지만, 그것만으로는 "다른 회사 회의"·"존재하지 않는
    // 회의"가 전부 빈 목록으로 뭉개져 구분이 안 된다 — 회의 소유는 meeting(D)의 공개 조회
    // 포트로 먼저 확인한다(코드래빗 지적, PR #229). ProjectQueryPort와 같은 직접 주입 패턴.
    @Override
    @Transactional(readOnly = true)
    public List<GetActionsByMeetingUseCase.MeetingActionItem> getActionsByMeeting(Long companyId, Long sourceMeetingId) {
        if (meetingQueryPort.findMeeting(companyId, sourceMeetingId).isEmpty()) {
            throw new BusinessException(ActionErrorCode.ACTION_MEETING_NOT_FOUND);
        }

        List<Action> actions = actionRepository.findAllByCompanyIdAndSourceMeetingId(companyId, sourceMeetingId);
        if (actions.isEmpty()) {
            return List.of();
        }

        Map<Long, String> assigneeNameById = toDisplayMap(
                actionReferenceRepository.findMemberReferences(distinctNonNull(actions, Action::getAssigneeMemberId)),
                MemberReference::memberId, MemberReference::name);
        Map<Long, String> teamNameById = toDisplayMap(
                actionReferenceRepository.findTeamReferences(distinctNonNull(actions, Action::getTeamId)),
                TeamReference::teamId, TeamReference::name);

        return actions.stream()
                .map(action -> new GetActionsByMeetingUseCase.MeetingActionItem(
                        action,
                        action.getAssigneeMemberId() == null ? null : assigneeNameById.get(action.getAssigneeMemberId()),
                        action.getTeamId() == null ? null : teamNameById.get(action.getTeamId())
                ))
                .toList();
    }

    private static <T> List<Long> distinct(List<Action> actions, Function<Action, Long> extractor) {
        return actions.stream().map(extractor).distinct().toList();
    }

    private static <T> List<Long> distinctNonNull(List<Action> actions, Function<Action, Long> extractor) {
        return actions.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, String> toDisplayMap(List<T> references, Function<T, Long> idFn, Function<T, String> nameFn) {
        return references.stream().collect(Collectors.toMap(idFn, nameFn));
    }
}
