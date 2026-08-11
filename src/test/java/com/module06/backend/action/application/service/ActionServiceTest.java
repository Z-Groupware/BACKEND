package com.module06.backend.action.application.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.command.BulkUpdateActionStatusCommand;
import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase.ActionDetail;
import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase.MeetingActionItem;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase.ActionListItem;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MemberReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MeetingReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.in.MeetingQueryPort;
import com.module06.backend.meeting.application.result.MeetingHistoryResult;
import com.module06.backend.project.application.port.ProjectQueryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long PROJECT = 100L;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ActionReferenceRepository actionReferenceRepository;

    @Mock
    private ProjectQueryPort projectQueryPort;

    @Mock
    private MeetingQueryPort meetingQueryPort;

    private ActionService actionService() {
        return new ActionService(actionRepository, actionReferenceRepository, projectQueryPort, meetingQueryPort);
    }

    @Test
    void createSavesManualPersonalActionAsHumanConfirmed() {
        ActionService service = actionService();
        when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);
        when(actionReferenceRepository.existsMemberInCompany(5L, COMPANY)).thenReturn(true);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Action saved = service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.PERSONAL, null, 5L, "수동 추가", "설명", LocalDate.of(2026, 12, 31)
        ));

        assertThat(saved.getStatus()).isEqualTo(ActionStatus.TODO);
        assertThat(saved.getReviewStatus()).isEqualTo(ActionReviewStatus.HUMAN_CONFIRMED);
        assertThat(saved.isManual()).isTrue();
        assertThat(saved.getAssigneeMemberId()).isEqualTo(5L);
        assertThat(saved.getConfirmedAt()).isNotNull();
    }

    @Test
    void createSavesManualTeamAction() {
        ActionService service = actionService();
        when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);
        when(actionReferenceRepository.existsTeamInCompany(7L, COMPANY)).thenReturn(true);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Action saved = service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.TEAM, 7L, null, "팀 액션 수동 추가", null, LocalDate.of(2026, 12, 31)
        ));

        assertThat(saved.getTeamId()).isEqualTo(7L);
        assertThat(saved.getAssigneeMemberId()).isNull();
    }

    @Test
    void createThrowsWhenProjectNotActiveOrOtherCompany() {
        ActionService service = actionService();
        when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.PERSONAL, null, 5L, "제목", null, LocalDate.now()
        ))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_PROJECT_NOT_FOUND);

        verify(actionRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenPersonalActionMissingAssignee() {
        ActionService service = actionService();
        lenient().when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.PERSONAL, null, null, "제목", null, LocalDate.now()
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(actionRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenPersonalActionAlsoCarriesTeamId() {
        ActionService service = actionService();
        lenient().when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.PERSONAL, 9L, 5L, "제목", null, LocalDate.now()
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(actionRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenTeamActionMissingTeamId() {
        ActionService service = actionService();
        lenient().when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.TEAM, null, null, "제목", null, LocalDate.now()
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(actionRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenAssigneeBelongsToAnotherCompany() {
        ActionService service = actionService();
        when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);
        when(actionReferenceRepository.existsMemberInCompany(5L, COMPANY)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.PERSONAL, null, 5L, "제목", null, LocalDate.now()
        ))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_ASSIGNEE_NOT_FOUND);

        verify(actionRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenTeamBelongsToAnotherCompany() {
        ActionService service = actionService();
        when(projectQueryPort.existsActiveProject(COMPANY, PROJECT)).thenReturn(true);
        when(actionReferenceRepository.existsTeamInCompany(7L, COMPANY)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateActionCommand(
                COMPANY, PROJECT, ActionType.TEAM, 7L, null, "제목", null, LocalDate.now()
        ))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_TEAM_NOT_FOUND);

        verify(actionRepository, never()).save(any());
    }

    // ── FR-AC-02 조회 ──────────────────────────────────────────────

    @Test
    void getMyActionsReturnsEnrichedListForCaller() {
        ActionService service = actionService();
        Action action = personalAction(10L, COMPANY, PROJECT, 7L, 200L, 300L, ActionStatus.TODO);
        when(actionRepository.countByAssigneeMemberId(5L, null, null)).thenReturn(1L);
        when(actionRepository.findAllByAssigneeMemberId(5L, null, null, null, "desc", 0, 20)).thenReturn(List.of(action));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이하윤", null)));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(7L)))
                .thenReturn(List.of(new TeamReference(7L, "개발팀", null)));
        when(actionReferenceRepository.findMeetingReferences(List.of(200L)))
                .thenReturn(List.of(new MeetingReference(200L, 7L, null, "기획 회의", null)));
        when(actionRepository.findAllByIds(List.of(300L)))
                .thenReturn(List.of(personalAction(300L, COMPANY, PROJECT, 7L, null, null, ActionStatus.TODO)));

        var result = service.getMyActions(5L, "MEMBER", null, null, null, null, null, "desc", 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        ActionListItem item = result.items().get(0);
        assertThat(item.assigneeName()).isEqualTo("이하윤");
        assertThat(item.projectTag()).isEqualTo("GOODS");
        assertThat(item.projectName()).isEqualTo("굿즈");
        assertThat(item.teamName()).isEqualTo("개발팀");
        assertThat(item.sourceMeetingTitle()).isEqualTo("기획 회의");
        assertThat(item.parentActionTitle()).isNotBlank();
        assertThat(item.action().getDescription()).isNotNull();
        assertThat(item.action().getProjectId()).isEqualTo(PROJECT);
    }

    @Test
    void getMyActionsReturnsEmptyListWithoutQueryingReferencesWhenCallerHasNoActions() {
        ActionService service = actionService();
        when(actionRepository.countByAssigneeMemberId(5L, null, null)).thenReturn(0L);
        when(actionRepository.findAllByAssigneeMemberId(5L, null, null, null, "desc", 0, 20)).thenReturn(List.of());

        assertThat(service.getMyActions(5L, "MEMBER", null, null, null, null, null, "desc", 0, 20).items()).isEmpty();
        verify(actionReferenceRepository, never()).findMemberReferences(anyList());
    }

    // ── 2026-08-11 팀장의 팀원 목록 조회(assigneeMemberId) ──────────────

    @Test
    void getMyActionsThrowsWhenNonLeaderSpecifiesAssigneeMemberId() {
        ActionService service = actionService();

        assertThatThrownBy(() -> service.getMyActions(5L, "MEMBER", 7L, 9L, null, null, null, "desc", 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.NOT_TEAM_LEADER);

        verify(actionReferenceRepository, never()).existsMemberInTeam(anyLong(), anyLong());
        verify(actionRepository, never()).countByAssigneeMemberId(any(), any(), any());
    }

    @Test
    void getMyActionsThrowsWhenTargetMemberIsOutsideLeaderTeam() {
        ActionService service = actionService();
        when(actionReferenceRepository.existsMemberInTeam(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.getMyActions(5L, "LEADER", 7L, 9L, null, null, null, "desc", 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_ASSIGNEE_OUT_OF_TEAM_SCOPE);

        verify(actionRepository, never()).countByAssigneeMemberId(any(), any(), any());
    }

    // CodeRabbit 지적(2026-08-11) — requesterTeamId가 null이면 Spring Data JPA의
    // "null 파라미터 → IS NULL" 변환 때문에 existsByIdAndTeamId(targetId, null)가 team_id가
    // 똑같이 NULL인 팀 무소속 대상과 우연히 매치할 수 있다. existsMemberInTeam을 아예 호출하지
    // 않고 막는지 검증한다.
    @Test
    void getMyActionsThrowsWhenRequesterHasNoTeamEvenIfTargetIsTeamless() {
        ActionService service = actionService();

        assertThatThrownBy(() -> service.getMyActions(5L, "LEADER", null, 9L, null, null, null, "desc", 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_ASSIGNEE_OUT_OF_TEAM_SCOPE);

        verify(actionReferenceRepository, never()).existsMemberInTeam(any(), any());
        verify(actionRepository, never()).countByAssigneeMemberId(any(), any(), any());
    }

    @Test
    void getMyActionsReturnsTargetMemberListWhenLeaderAndSameTeam() {
        ActionService service = actionService();
        Action action = personalAction(20L, COMPANY, PROJECT, 7L, null, null, ActionStatus.TODO, 9L);
        when(actionReferenceRepository.existsMemberInTeam(9L, 7L)).thenReturn(true);
        when(actionRepository.countByAssigneeMemberId(9L, null, null)).thenReturn(1L);
        when(actionRepository.findAllByAssigneeMemberId(9L, null, null, null, "desc", 0, 20)).thenReturn(List.of(action));
        when(actionReferenceRepository.findMemberReferences(List.of(9L)))
                .thenReturn(List.of(new MemberReference(9L, "박도현", null)));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(7L)))
                .thenReturn(List.of(new TeamReference(7L, "개발팀", null)));

        var result = service.getMyActions(5L, "LEADER", 7L, 9L, null, null, null, "desc", 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).assigneeName()).isEqualTo("박도현");
    }

    @Test
    void getActionDetailReturnsEnrichedDetailForOwnCompany() {
        ActionService service = actionService();
        Action action = personalAction(10L, COMPANY, PROJECT, 7L, 200L, 999L, ActionStatus.IN_PROGRESS);
        java.time.LocalDateTime scheduledAt = java.time.LocalDateTime.of(2026, 8, 12, 14, 0);
        when(actionRepository.findById(10L)).thenReturn(java.util.Optional.of(action));
        when(actionRepository.findById(999L))
                .thenReturn(java.util.Optional.of(teamAction(999L, COMPANY, PROJECT, 8L, null, ActionStatus.TODO)));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이하윤", 50L)));
        when(actionReferenceRepository.findSubTeamReferences(List.of(50L)))
                .thenReturn(List.of(new com.module06.backend.action.domain.repository.ActionReferenceRepository.SubTeamReference(50L, "프론트엔드")));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "연예인 굿즈 쇼핑몰 앱 구축")));
        when(actionReferenceRepository.findTeamReferences(List.of(7L)))
                .thenReturn(List.of(new TeamReference(7L, "개발팀", null)));
        when(actionReferenceRepository.findTeamReferences(List.of(8L)))
                .thenReturn(List.of(new TeamReference(8L, "마케팅팀", null)));
        when(actionReferenceRepository.findMeetingReferences(List.of(200L)))
                .thenReturn(List.of(new MeetingReference(200L, 7L, null, "기획 회의", scheduledAt)));

        ActionDetail detail = service.getActionDetail(COMPANY, 10L);

        assertThat(detail.assigneeName()).isEqualTo("이하윤");
        assertThat(detail.assigneeRoleLabel()).isEqualTo("프론트엔드");
        assertThat(detail.projectName()).isEqualTo("연예인 굿즈 쇼핑몰 앱 구축");
        assertThat(detail.teamName()).isEqualTo("개발팀");
        assertThat(detail.sourceMeetingTitle()).isEqualTo("기획 회의");
        assertThat(detail.sourceMeetingScheduledAt()).isEqualTo(scheduledAt);
        assertThat(detail.parentActionTeamName()).isEqualTo("마케팅팀");
        assertThat(detail.parentActionDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(detail.action().getProjectId()).isEqualTo(PROJECT);
    }

    @Test
    void getActionDetailLeavesNewFieldsNullWhenNotApplicable() {
        ActionService service = actionService();
        Action action = personalAction(11L, COMPANY, PROJECT, null, null, null, ActionStatus.TODO);
        when(actionRepository.findById(11L)).thenReturn(java.util.Optional.of(action));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이하윤", null)));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "연예인 굿즈 쇼핑몰 앱 구축")));

        ActionDetail detail = service.getActionDetail(COMPANY, 11L);

        assertThat(detail.assigneeRoleLabel()).isNull();
        assertThat(detail.sourceMeetingScheduledAt()).isNull();
        assertThat(detail.parentActionTeamName()).isNull();
        assertThat(detail.parentActionDueDate()).isNull();
        verify(actionReferenceRepository, never()).findSubTeamReferences(anyList());
    }

    @Test
    void getActionDetailThrowsNotFoundWhenActionBelongsToAnotherCompany() {
        ActionService service = actionService();
        Action action = personalAction(10L, 999L, PROJECT, 7L, null, null, ActionStatus.TODO);
        when(actionRepository.findById(10L)).thenReturn(java.util.Optional.of(action));

        assertThatThrownBy(() -> service.getActionDetail(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    @Test
    void getActionDetailThrowsNotFoundWhenActionMissing() {
        ActionService service = actionService();
        when(actionRepository.findById(10L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getActionDetail(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    // ── FR-AC-03 상태변경(벌크) ─────────────────────────────────────

    @Test
    void bulkUpdateStatusChangesAllItemsWhenRequesterIsAssigneeForEach() {
        ActionService service = actionService();
        Action first = personalAction(1L, COMPANY, PROJECT, null, null, null, ActionStatus.TODO);
        Action second = personalAction(2L, COMPANY, PROJECT, null, null, null, ActionStatus.IN_PROGRESS);
        when(actionRepository.findAllByIds(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(actionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.bulkUpdateStatus(new BulkUpdateActionStatusCommand(5L, List.of(
                new BulkUpdateActionStatusCommand.Item(1L, ActionStatus.IN_PROGRESS),
                new BulkUpdateActionStatusCommand.Item(2L, ActionStatus.DONE)
        )));

        assertThat(first.getStatus()).isEqualTo(ActionStatus.IN_PROGRESS);
        assertThat(first.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(second.getStatus()).isEqualTo(ActionStatus.DONE);
        verify(actionRepository).saveAll(any());
    }

    @Test
    void bulkUpdateStatusReopensFromDoneBackToInProgressWithoutTouchingStartDate() {
        ActionService service = actionService();
        Action action = personalAction(1L, COMPANY, PROJECT, null, null, null, ActionStatus.DONE);
        LocalDate startDateBefore = action.getStartDate();
        when(actionRepository.findAllByIds(List.of(1L))).thenReturn(List.of(action));
        when(actionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.bulkUpdateStatus(new BulkUpdateActionStatusCommand(5L, List.of(
                new BulkUpdateActionStatusCommand.Item(1L, ActionStatus.IN_PROGRESS)
        )));

        assertThat(action.getStatus()).isEqualTo(ActionStatus.IN_PROGRESS);
        assertThat(action.getStartDate()).isEqualTo(startDateBefore);
    }

    @Test
    void bulkUpdateStatusRejectsTodoAsTargetSinceItIsUnreachable() {
        ActionService service = actionService();
        Action action = personalAction(1L, COMPANY, PROJECT, null, null, null, ActionStatus.IN_PROGRESS);
        when(actionRepository.findAllByIds(List.of(1L))).thenReturn(List.of(action));

        assertThatThrownBy(() -> service.bulkUpdateStatus(new BulkUpdateActionStatusCommand(5L, List.of(
                new BulkUpdateActionStatusCommand.Item(1L, ActionStatus.TODO)
        )))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_INVALID_STATUS_TRANSITION);

        verify(actionRepository, never()).saveAll(any());
    }

    @Test
    void bulkUpdateStatusRejectsTodoToDoneSkippingInProgress() {
        ActionService service = actionService();
        Action action = personalAction(1L, COMPANY, PROJECT, null, null, null, ActionStatus.TODO);
        when(actionRepository.findAllByIds(List.of(1L))).thenReturn(List.of(action));

        assertThatThrownBy(() -> service.bulkUpdateStatus(new BulkUpdateActionStatusCommand(5L, List.of(
                new BulkUpdateActionStatusCommand.Item(1L, ActionStatus.DONE)
        )))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_INVALID_STATUS_TRANSITION);

        assertThat(action.getStatus()).isEqualTo(ActionStatus.TODO);
        verify(actionRepository, never()).saveAll(any());
    }

    @Test
    void bulkUpdateStatusThrowsAndSavesNothingWhenOneItemIsNotOwnedByRequester() {
        ActionService service = actionService();
        Action owned = personalAction(1L, COMPANY, PROJECT, null, null, null, ActionStatus.IN_PROGRESS);
        Action notOwned = personalAction(2L, COMPANY, PROJECT, null, null, null, ActionStatus.IN_PROGRESS, 9L);
        when(actionRepository.findAllByIds(List.of(1L, 2L))).thenReturn(List.of(owned, notOwned));

        assertThatThrownBy(() -> service.bulkUpdateStatus(new BulkUpdateActionStatusCommand(5L, List.of(
                new BulkUpdateActionStatusCommand.Item(1L, ActionStatus.DONE),
                new BulkUpdateActionStatusCommand.Item(2L, ActionStatus.DONE)
        )))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.NOT_ACTION_ASSIGNEE);

        assertThat(owned.getStatus()).isEqualTo(ActionStatus.IN_PROGRESS);
        verify(actionRepository, never()).saveAll(any());
    }

    @Test
    void bulkUpdateStatusThrowsWhenAnItemActionDoesNotExist() {
        ActionService service = actionService();
        when(actionRepository.findAllByIds(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.bulkUpdateStatus(new BulkUpdateActionStatusCommand(5L, List.of(
                new BulkUpdateActionStatusCommand.Item(1L, ActionStatus.DONE)
        )))).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);

        verify(actionRepository, never()).saveAll(any());
    }

    @Test
    void getActionsByMeetingReturnsMixedTeamAndPersonalActionsWithSeparateDisplayFields() {
        ActionService service = actionService();
        when(meetingQueryPort.findMeeting(COMPANY, 200L)).thenReturn(java.util.Optional.of(meetingHistory(200L)));
        Action teamAction = teamAction(1L, COMPANY, PROJECT, 7L, 200L, ActionStatus.TODO);
        Action personalAction = personalAction(2L, COMPANY, PROJECT, null, 200L, null, ActionStatus.TODO, 5L);
        when(actionRepository.findAllByCompanyIdAndSourceMeetingId(COMPANY, 200L))
                .thenReturn(List.of(teamAction, personalAction));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이하윤", null)));
        when(actionReferenceRepository.findTeamReferences(List.of(7L)))
                .thenReturn(List.of(new TeamReference(7L, "개발팀", null)));

        List<MeetingActionItem> result = service.getActionsByMeeting(COMPANY, 200L);

        assertThat(result).hasSize(2);
        MeetingActionItem teamItem = result.stream().filter(i -> i.action().getId().equals(1L)).findFirst().orElseThrow();
        assertThat(teamItem.teamName()).isEqualTo("개발팀");
        assertThat(teamItem.assigneeName()).isNull();
        MeetingActionItem personalItem = result.stream().filter(i -> i.action().getId().equals(2L)).findFirst().orElseThrow();
        assertThat(personalItem.assigneeName()).isEqualTo("이하윤");
        assertThat(personalItem.teamName()).isNull();
    }

    @Test
    void getActionsByMeetingReturnsEmptyListWithoutQueryingReferencesWhenMeetingHasNoActions() {
        ActionService service = actionService();
        when(meetingQueryPort.findMeeting(COMPANY, 200L)).thenReturn(java.util.Optional.of(meetingHistory(200L)));
        when(actionRepository.findAllByCompanyIdAndSourceMeetingId(COMPANY, 200L)).thenReturn(List.of());

        assertThat(service.getActionsByMeeting(COMPANY, 200L)).isEmpty();
        verify(actionReferenceRepository, never()).findMemberReferences(anyList());
        verify(actionReferenceRepository, never()).findTeamReferences(anyList());
    }

    @Test
    void getActionsByMeetingThrowsWhenMeetingDoesNotBelongToCallerCompany() {
        // 다른 회사 회의·존재하지 않는 회의를 "액션 없음"과 구분해야 한다(코드래빗 지적, PR #229).
        ActionService service = actionService();
        when(meetingQueryPort.findMeeting(COMPANY, 999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getActionsByMeeting(COMPANY, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_MEETING_NOT_FOUND);

        verify(actionRepository, never()).findAllByCompanyIdAndSourceMeetingId(any(), any());
    }

    private MeetingHistoryResult meetingHistory(Long meetingId) {
        return new MeetingHistoryResult(
                meetingId, PROJECT, "회의 제목", null, null, null, null, null, null, List.of());
    }

    private Action teamAction(Long id, Long companyId, Long projectId, Long teamId,
                               Long sourceMeetingId, ActionStatus status) {
        boolean isDone = status == ActionStatus.DONE;
        LocalDate startDate = status == ActionStatus.TODO ? null : LocalDate.of(2026, 8, 1);
        return Action.reconstitute(
                id, companyId, projectId, null, sourceMeetingId, teamId, null,
                ActionType.TEAM, "팀 액션 " + id, "설명", isDone, startDate, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
    }

    private Action personalAction(Long id, Long companyId, Long projectId, Long teamId,
                                   Long sourceMeetingId, Long parentActionId, ActionStatus status) {
        return personalAction(id, companyId, projectId, teamId, sourceMeetingId, parentActionId, status, 5L);
    }

    private Action personalAction(Long id, Long companyId, Long projectId, Long teamId,
                                   Long sourceMeetingId, Long parentActionId, ActionStatus status, Long assigneeMemberId) {
        boolean isDone = status == ActionStatus.DONE;
        LocalDate startDate = status == ActionStatus.TODO ? null : LocalDate.of(2026, 8, 1);
        return Action.reconstitute(
                id, companyId, projectId, parentActionId, sourceMeetingId, teamId, assigneeMemberId,
                ActionType.PERSONAL, "액션 " + id, "설명", isDone, startDate, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
    }
}
