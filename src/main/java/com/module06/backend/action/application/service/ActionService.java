package com.module06.backend.action.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
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

    // FR-AC-02 목록 — 호출자 본인 소유 PERSONAL 액션만, 표시값은 배치조회로 조인한다.
    @Override
    @Transactional(readOnly = true)
    public List<GetMyActionsUseCase.ActionListItem> getMyActions(Long assigneeMemberId) {
        List<Action> actions = actionRepository.findAllByAssigneeMemberId(assigneeMemberId);
        if (actions.isEmpty()) {
            return List.of();
        }

        // 담당자는 호출자 본인 한 명뿐이라 단건 조회로 충분하다.
        String assigneeName = actionReferenceRepository.findMemberReferences(List.of(assigneeMemberId)).stream()
                .findFirst().map(MemberReference::name).orElse(null);

        Map<Long, String> projectTagById = toDisplayMap(
                actionReferenceRepository.findProjectReferences(distinct(actions, Action::getProjectId)),
                ProjectReference::projectId, ProjectReference::tag);
        Map<Long, String> teamNameById = toDisplayMap(
                actionReferenceRepository.findTeamReferences(distinctNonNull(actions, Action::getTeamId)),
                TeamReference::teamId, TeamReference::name);
        Map<Long, String> meetingTitleById = toDisplayMap(
                actionReferenceRepository.findMeetingReferences(distinctNonNull(actions, Action::getSourceMeetingId)),
                MeetingReference::meetingId, MeetingReference::title);
        Map<Long, String> parentTitleById = actionRepository.findAllByIds(distinctNonNull(actions, Action::getParentActionId))
                .stream().collect(Collectors.toMap(Action::getId, Action::getTitle));

        return actions.stream()
                .map(action -> new GetMyActionsUseCase.ActionListItem(
                        action,
                        assigneeName,
                        projectTagById.get(action.getProjectId()),
                        action.getTeamId() == null ? null : teamNameById.get(action.getTeamId()),
                        action.getSourceMeetingId() == null ? null : meetingTitleById.get(action.getSourceMeetingId()),
                        action.getParentActionId() == null ? null : parentTitleById.get(action.getParentActionId())
                ))
                .toList();
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

        String assigneeName = action.getAssigneeMemberId() == null ? null
                : actionReferenceRepository.findMemberReferences(List.of(action.getAssigneeMemberId())).stream()
                        .findFirst().map(MemberReference::name).orElse(null);

        ProjectReference project = actionReferenceRepository.findProjectReferences(List.of(action.getProjectId())).stream()
                .findFirst().orElse(null);

        String teamName = action.getTeamId() == null ? null
                : actionReferenceRepository.findTeamReferences(List.of(action.getTeamId())).stream()
                        .findFirst().map(TeamReference::name).orElse(null);

        String sourceMeetingTitle = action.getSourceMeetingId() == null ? null
                : actionReferenceRepository.findMeetingReferences(List.of(action.getSourceMeetingId())).stream()
                        .findFirst().map(MeetingReference::title).orElse(null);

        String parentActionTitle = action.getParentActionId() == null ? null
                : actionRepository.findById(action.getParentActionId()).map(Action::getTitle).orElse(null);

        return new GetActionDetailUseCase.ActionDetail(
                action, assigneeName,
                project == null ? null : project.tag(),
                project == null ? null : project.name(),
                teamName, sourceMeetingTitle, parentActionTitle
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

        // 반영 단계 — 목표 칸에 따라 실제로 쓰는 필드가 다르다.
        for (BulkUpdateActionStatusCommand.Item item : command.items()) {
            applyTransition(actionsById.get(item.actionId()), item.status());
        }

        actionRepository.saveAll(List.copyOf(actionsById.values()));
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
