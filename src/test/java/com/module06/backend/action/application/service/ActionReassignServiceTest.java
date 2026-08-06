package com.module06.backend.action.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.port.ActionReassignPort.HandoverScope;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionReassignService")
class ActionReassignServiceTest {

    @Mock
    private ActionRepository actionRepository;

    @InjectMocks
    private ActionReassignService service;

    @Test
    @DisplayName("VACATION handoverable actions exclude DONE and map record fields")
    void vacationFindsPersonalActionsExcludingDone() {
        Action todo = personal(1L, 10L, ActionStatus.TODO);
        when(actionRepository.findPersonalByAssignee(10L, true)).thenReturn(List.of(todo));

        var result = service.findHandoverableActions(10L, HandoverScope.VACATION);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.actionId()).isEqualTo(1L);
            assertThat(view.title()).isEqualTo("Action 1");
            assertThat(view.projectId()).isEqualTo(100L);
            assertThat(view.actionType()).isEqualTo("PERSONAL");
            assertThat(view.status()).isEqualTo("TODO");
            assertThat(view.deadline()).isEqualTo(LocalDate.of(2026, 8, 20));
            assertThat(view.sourceMeetingId()).isEqualTo(300L);
            assertThat(view.content()).isEqualTo("Content 1");
        });
        verify(actionRepository).findPersonalByAssignee(10L, true);
    }

    @Test
    @DisplayName("OFFBOARDING handoverable actions include DONE")
    void offboardingFindsPersonalActionsIncludingDone() {
        Action done = personal(2L, 10L, ActionStatus.DONE);
        when(actionRepository.findPersonalByAssignee(10L, false)).thenReturn(List.of(done));

        var result = service.findHandoverableActions(10L, HandoverScope.OFFBOARDING);

        assertThat(result).singleElement()
                .satisfies(view -> assertThat(view.status()).isEqualTo("DONE"));
        verify(actionRepository).findPersonalByAssignee(10L, false);
    }

    @Test
    @DisplayName("type-agnostic handoverable actions use all personal actions")
    void typeAgnosticFindsAllPersonalActions() {
        Action done = personal(3L, 10L, ActionStatus.DONE);
        when(actionRepository.findAllPersonalByAssignee(10L)).thenReturn(List.of(done));

        var result = service.findHandoverableActions(10L);

        assertThat(result).singleElement()
                .satisfies(view -> assertThat(view.status()).isEqualTo("DONE"));
        verify(actionRepository).findAllPersonalByAssignee(10L);
    }

    @Test
    @DisplayName("reassign changes assignee and saves")
    void reassignChangesAssigneeAndSaves() {
        Action action = personal(4L, 10L, ActionStatus.IN_PROGRESS);
        when(actionRepository.findById(4L)).thenReturn(Optional.of(action));
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reassign(4L, 10L, 20L);

        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(actionRepository).save(captor.capture());
        assertThat(captor.getValue().getAssigneeMemberId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("reassign throws ACTION_NOT_FOUND when action is missing")
    void reassignThrowsActionNotFound() {
        when(actionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(404L, 10L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.ACTION_NOT_FOUND));
        verify(actionRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassign throws CANNOT_REASSIGN_TEAM_ACTION for TEAM action")
    void reassignThrowsForTeamAction() {
        when(actionRepository.findById(5L)).thenReturn(Optional.of(team(5L)));

        assertThatThrownBy(() -> service.reassign(5L, 10L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.CANNOT_REASSIGN_TEAM_ACTION));
        verify(actionRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassign throws NOT_ACTION_ASSIGNEE when assignee is null")
    void reassignThrowsWhenAssigneeNull() {
        when(actionRepository.findById(6L)).thenReturn(Optional.of(personal(6L, null, ActionStatus.TODO)));

        assertThatThrownBy(() -> service.reassign(6L, 10L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.NOT_ACTION_ASSIGNEE));
        verify(actionRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassign throws NOT_ACTION_ASSIGNEE when from member does not match")
    void reassignThrowsWhenFromMemberMismatch() {
        when(actionRepository.findById(7L)).thenReturn(Optional.of(personal(7L, 99L, ActionStatus.TODO)));

        assertThatThrownBy(() -> service.reassign(7L, 10L, 20L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ActionErrorCode.NOT_ACTION_ASSIGNEE));
        verify(actionRepository, never()).save(any());
    }

    @Test
    @DisplayName("departure team actions map parent TEAM records")
    void findsTeamActionsForDeparture() {
        when(actionRepository.findParentTeamActionsByAssignee(10L)).thenReturn(List.of(team(8L), team(9L)));

        var result = service.findTeamActionsForDeparture(10L);

        assertThat(result).extracting("actionId").containsExactly(8L, 9L);
        assertThat(result.get(0)).satisfies(view -> {
            assertThat(view.title()).isEqualTo("Team Action 8");
            assertThat(view.projectId()).isEqualTo(100L);
            assertThat(view.sourceMeetingId()).isEqualTo(300L);
            assertThat(view.status()).isEqualTo("IN_PROGRESS");
            assertThat(view.teamId()).isEqualTo(700L);
        });
        verify(actionRepository).findParentTeamActionsByAssignee(10L);
    }

    private Action personal(Long id, Long assigneeMemberId, ActionStatus status) {
        return new Action(
                id,
                1L,
                100L,
                500L,
                300L,
                null,
                assigneeMemberId,
                ActionType.PERSONAL,
                "Action " + id,
                "Content " + id,
                status,
                LocalDate.of(2026, 8, 20),
                false,
                null,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
    }

    private Action team(Long id) {
        return new Action(
                id,
                1L,
                100L,
                null,
                300L,
                700L,
                null,
                ActionType.TEAM,
                "Team Action " + id,
                "Team Content " + id,
                ActionStatus.IN_PROGRESS,
                LocalDate.of(2026, 8, 30),
                false,
                null,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
    }
}
