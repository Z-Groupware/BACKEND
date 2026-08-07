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
import com.module06.backend.project.application.port.ProjectQueryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    private ActionService actionService() {
        return new ActionService(actionRepository, actionReferenceRepository, projectQueryPort);
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
        when(actionRepository.findAllByAssigneeMemberId(5L)).thenReturn(List.of(action));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이하윤")));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(7L)))
                .thenReturn(List.of(new TeamReference(7L, "개발팀")));
        when(actionReferenceRepository.findMeetingReferences(List.of(200L)))
                .thenReturn(List.of(new MeetingReference(200L, 7L, null, "기획 회의")));
        when(actionRepository.findAllByIds(List.of(300L)))
                .thenReturn(List.of(personalAction(300L, COMPANY, PROJECT, 7L, null, null, ActionStatus.TODO)));

        List<ActionListItem> result = service.getMyActions(5L);

        assertThat(result).hasSize(1);
        ActionListItem item = result.get(0);
        assertThat(item.assigneeName()).isEqualTo("이하윤");
        assertThat(item.projectTag()).isEqualTo("GOODS");
        assertThat(item.teamName()).isEqualTo("개발팀");
        assertThat(item.sourceMeetingTitle()).isEqualTo("기획 회의");
        assertThat(item.parentActionTitle()).isNotBlank();
    }

    @Test
    void getMyActionsReturnsEmptyListWithoutQueryingReferencesWhenCallerHasNoActions() {
        ActionService service = actionService();
        when(actionRepository.findAllByAssigneeMemberId(5L)).thenReturn(List.of());

        assertThat(service.getMyActions(5L)).isEmpty();
        verify(actionReferenceRepository, never()).findMemberReferences(anyList());
    }

    @Test
    void getActionDetailReturnsEnrichedDetailForOwnCompany() {
        ActionService service = actionService();
        Action action = personalAction(10L, COMPANY, PROJECT, 7L, 200L, null, ActionStatus.IN_PROGRESS);
        when(actionRepository.findById(10L)).thenReturn(java.util.Optional.of(action));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이하윤")));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "연예인 굿즈 쇼핑몰 앱 구축")));
        when(actionReferenceRepository.findTeamReferences(List.of(7L)))
                .thenReturn(List.of(new TeamReference(7L, "개발팀")));
        when(actionReferenceRepository.findMeetingReferences(List.of(200L)))
                .thenReturn(List.of(new MeetingReference(200L, 7L, null, "기획 회의")));

        ActionDetail detail = service.getActionDetail(COMPANY, 10L);

        assertThat(detail.assigneeName()).isEqualTo("이하윤");
        assertThat(detail.projectName()).isEqualTo("연예인 굿즈 쇼핑몰 앱 구축");
        assertThat(detail.teamName()).isEqualTo("개발팀");
        assertThat(detail.sourceMeetingTitle()).isEqualTo("기획 회의");
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
