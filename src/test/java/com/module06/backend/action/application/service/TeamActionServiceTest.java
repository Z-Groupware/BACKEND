package com.module06.backend.action.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase.TeamActionDetail;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase.TeamActionListItem;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase.TimelineItem;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.AttachmentReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MemberReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamActionServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long PROJECT = 100L;
    private static final Long TEAM = 7L;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ActionReferenceRepository actionReferenceRepository;

    private TeamActionService teamActionService() {
        return new TeamActionService(actionRepository, actionReferenceRepository);
    }

    // ── FR-AC-06 목록 ──────────────────────────────────────────────

    @Test
    void getTeamActionsReturnsEnrichedListScopedByTeamId() {
        TeamActionService service = teamActionService();
        Action action = teamAction(10L, ActionStatus.TODO);
        when(actionRepository.findAllByTeamId(TEAM)).thenReturn(List.of(action));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(TEAM)))
                .thenReturn(List.of(new TeamReference(TEAM, "개발팀")));

        List<TeamActionListItem> result = service.getTeamActions(TEAM);

        assertThat(result).hasSize(1);
        TeamActionListItem item = result.get(0);
        assertThat(item.action()).isEqualTo(action);
        assertThat(item.projectTag()).isEqualTo("GOODS");
        assertThat(item.teamName()).isEqualTo("개발팀");
    }

    @Test
    void getTeamActionsReturnsEmptyListWithoutQueryingReferencesWhenTeamHasNoActions() {
        TeamActionService service = teamActionService();
        when(actionRepository.findAllByTeamId(TEAM)).thenReturn(List.of());

        assertThat(service.getTeamActions(TEAM)).isEmpty();
        verify(actionReferenceRepository, never()).findProjectReferences(anyList());
        verify(actionReferenceRepository, never()).findTeamReferences(anyList());
    }

    // ── FR-AC-06 상세 ──────────────────────────────────────────────

    @Test
    void getTeamActionDetailReturnsEnrichedDetailWithAttachmentsForOwnCompany() {
        TeamActionService service = teamActionService();
        Action action = teamAction(10L, ActionStatus.IN_PROGRESS);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(TEAM)))
                .thenReturn(List.of(new TeamReference(TEAM, "개발팀")));
        when(actionReferenceRepository.findProjectAttachments(PROJECT))
                .thenReturn(List.of(new AttachmentReference(1L, "기획서.pdf", "https://s3/x", 1024L, LocalDateTime.now())));

        TeamActionDetail detail = service.getTeamActionDetail(COMPANY, 10L);

        assertThat(detail.projectTag()).isEqualTo("GOODS");
        assertThat(detail.teamName()).isEqualTo("개발팀");
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.attachments().get(0).fileName()).isEqualTo("기획서.pdf");
    }

    @Test
    void getTeamActionDetailThrowsNotFoundWhenActionBelongsToAnotherCompany() {
        TeamActionService service = teamActionService();
        Action action = Action.reconstitute(
                10L, 999L, PROJECT, null, null, TEAM, null,
                ActionType.TEAM, "팀 액션", "설명", false, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.getTeamActionDetail(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    @Test
    void getTeamActionDetailThrowsNotFoundWhenActionIsPersonalNotTeam() {
        TeamActionService service = teamActionService();
        Action personal = Action.reconstitute(
                10L, COMPANY, PROJECT, null, null, null, 5L,
                ActionType.PERSONAL, "개인 액션", "설명", false, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
        when(actionRepository.findById(10L)).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> service.getTeamActionDetail(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    @Test
    void getTeamActionDetailThrowsNotFoundWhenActionMissing() {
        TeamActionService service = teamActionService();
        when(actionRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTeamActionDetail(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    // ── FR-AC-08 타임라인 ──────────────────────────────────────────

    @Test
    void getTeamActionTimelineReturnsChildPersonalActionsWithAssigneeNames() {
        TeamActionService service = teamActionService();
        Action teamAction = teamAction(10L, ActionStatus.IN_PROGRESS);
        Action child = personalAction(11L, 5L);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(teamAction));
        when(actionRepository.findAllByParentActionId(10L)).thenReturn(List.of(child));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이태연")));

        List<TimelineItem> result = service.getTeamActionTimeline(COMPANY, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo(child);
        assertThat(result.get(0).assigneeName()).isEqualTo("이태연");
    }

    @Test
    void getTeamActionTimelineReturnsEmptyListWithoutQueryingReferencesWhenNoChildren() {
        TeamActionService service = teamActionService();
        Action teamAction = teamAction(10L, ActionStatus.TODO);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(teamAction));
        when(actionRepository.findAllByParentActionId(10L)).thenReturn(List.of());

        assertThat(service.getTeamActionTimeline(COMPANY, 10L)).isEmpty();
        verify(actionReferenceRepository, never()).findMemberReferences(anyList());
    }

    @Test
    void getTeamActionTimelineThrowsNotFoundWhenActionBelongsToAnotherCompany() {
        TeamActionService service = teamActionService();
        Action action = Action.reconstitute(
                10L, 999L, PROJECT, null, null, TEAM, null,
                ActionType.TEAM, "팀 액션", "설명", false, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.getTeamActionTimeline(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    @Test
    void getTeamActionTimelineThrowsNotFoundWhenActionIsPersonalNotTeam() {
        TeamActionService service = teamActionService();
        Action personal = personalAction(10L, 5L);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> service.getTeamActionTimeline(COMPANY, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_NOT_FOUND);
    }

    private Action personalAction(Long id, Long assigneeMemberId) {
        return Action.reconstitute(
                id, COMPANY, PROJECT, 10L, null, null, assigneeMemberId,
                ActionType.PERSONAL, "개인 액션 " + id, "설명", false, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
    }

    private Action teamAction(Long id, ActionStatus status) {
        boolean isDone = status == ActionStatus.DONE;
        LocalDate startDate = status == ActionStatus.TODO ? null : LocalDate.of(2026, 8, 1);
        return Action.reconstitute(
                id, COMPANY, PROJECT, null, null, TEAM, null,
                ActionType.TEAM, "팀 액션 " + id, "설명", isDone, startDate, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
    }
}
