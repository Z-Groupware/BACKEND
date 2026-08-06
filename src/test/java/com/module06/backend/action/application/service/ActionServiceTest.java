package com.module06.backend.action.application.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.port.ProjectQueryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
