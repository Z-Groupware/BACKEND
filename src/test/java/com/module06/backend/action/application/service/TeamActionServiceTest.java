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
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase.TimelineItem;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase.TeamActionListItem;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.AttachmentReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MeetingReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MemberReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;

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

    @Mock
    private ProjectAttachmentStoragePort projectAttachmentStoragePort;

    private TeamActionService teamActionService() {
        return new TeamActionService(actionRepository, actionReferenceRepository, projectAttachmentStoragePort);
    }

    // ── FR-AC-06 목록 ──────────────────────────────────────────────

    @Test
    void getTeamActionsReturnsEnrichedListScopedByTeamId() {
        TeamActionService service = teamActionService();
        Action action = teamAction(10L, ActionStatus.TODO);
        when(actionRepository.countByTeamId(TEAM, null)).thenReturn(1L);
        when(actionRepository.findAllByTeamId(TEAM, null, null, "desc", 0, 20)).thenReturn(List.of(action));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(TEAM)))
                .thenReturn(List.of(new TeamReference(TEAM, "개발팀", null)));

        var result = service.getTeamActions(TEAM, null, null, "desc", 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        TeamActionListItem item = result.items().get(0);
        assertThat(item.action()).isEqualTo(action);
        assertThat(item.projectTag()).isEqualTo("GOODS");
        assertThat(item.projectName()).isEqualTo("굿즈");
        assertThat(item.teamName()).isEqualTo("개발팀");
    }

    @Test
    void getTeamActionsReturnsEmptyListWithoutQueryingReferencesWhenTeamHasNoActions() {
        TeamActionService service = teamActionService();
        when(actionRepository.countByTeamId(TEAM, null)).thenReturn(0L);
        when(actionRepository.findAllByTeamId(TEAM, null, null, "desc", 0, 20)).thenReturn(List.of());

        assertThat(service.getTeamActions(TEAM, null, null, "desc", 0, 20).items()).isEmpty();
        verify(actionReferenceRepository, never()).findProjectReferences(anyList());
        verify(actionReferenceRepository, never()).findTeamReferences(anyList());
    }

    // ── 팀 대시보드 KPI(이슈 #352) ──────────────────────────────────────

    @Test
    void getTeamDashboardSummaryAggregatesAllFourCards() {
        TeamActionService service = teamActionService();
        Long requester = 55L;
        when(actionRepository.countByTeamId(TEAM, ActionStatus.IN_PROGRESS)).thenReturn(3L);
        when(actionRepository.countTeamMemberActionsByTeamId(TEAM)).thenReturn(6L);
        when(actionRepository.countByAssigneeMemberId(requester, ActionStatus.TODO, null)).thenReturn(1L);
        when(actionRepository.countByAssigneeMemberId(requester, ActionStatus.IN_PROGRESS, null)).thenReturn(0L);
        when(actionRepository.countByAssigneeMemberId(requester, ActionStatus.DONE, null)).thenReturn(4L);

        var result = service.getTeamDashboardSummary(TEAM, requester);

        assertThat(result.teamActionCount()).isEqualTo(3L);
        assertThat(result.teamMemberActionCount()).isEqualTo(6L);
        assertThat(result.myActionCount()).isEqualTo(1L);
        assertThat(result.completedActionCount()).isEqualTo(4L);
    }

    @Test
    void getTeamDashboardSummarySumsTodoAndInProgressForMyActionCount() {
        TeamActionService service = teamActionService();
        Long requester = 55L;
        when(actionRepository.countByTeamId(TEAM, ActionStatus.IN_PROGRESS)).thenReturn(0L);
        when(actionRepository.countTeamMemberActionsByTeamId(TEAM)).thenReturn(0L);
        when(actionRepository.countByAssigneeMemberId(requester, ActionStatus.TODO, null)).thenReturn(2L);
        when(actionRepository.countByAssigneeMemberId(requester, ActionStatus.IN_PROGRESS, null)).thenReturn(3L);
        when(actionRepository.countByAssigneeMemberId(requester, ActionStatus.DONE, null)).thenReturn(0L);

        var result = service.getTeamDashboardSummary(TEAM, requester);

        assertThat(result.myActionCount()).isEqualTo(5L);
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
                .thenReturn(List.of(new TeamReference(TEAM, "개발팀", null)));
        when(actionReferenceRepository.findProjectAttachments(PROJECT))
                .thenReturn(List.of(new AttachmentReference(1L, "기획서.pdf", "https://s3/x", 1024L, LocalDateTime.now())));

        TeamActionDetail detail = service.getTeamActionDetail(COMPANY, 10L);

        assertThat(detail.projectTag()).isEqualTo("GOODS");
        assertThat(detail.teamName()).isEqualTo("개발팀");
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.attachments().get(0).fileName()).isEqualTo("기획서.pdf");
    }

    @Test
    void getTeamActionDetailDerivesAssigneeFromTeamLeaderAndIncludesSourceMeeting() {
        TeamActionService service = teamActionService();
        Action action = Action.reconstitute(
                10L, COMPANY, PROJECT, null, 200L, TEAM, null,
                ActionType.TEAM, "팀 액션", "설명", false, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 12, 14, 0);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(TEAM)))
                .thenReturn(List.of(new TeamReference(TEAM, "개발팀", 5L)));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "홍길동", null)));
        when(actionReferenceRepository.findMeetingReferences(List.of(200L)))
                .thenReturn(List.of(new MeetingReference(200L, TEAM, null, "기획 회의", scheduledAt)));
        when(actionReferenceRepository.findProjectAttachments(PROJECT)).thenReturn(List.of());

        TeamActionDetail detail = service.getTeamActionDetail(COMPANY, 10L);

        assertThat(detail.assigneeName()).isEqualTo("홍길동");
        assertThat(detail.assigneeRoleLabel()).isEqualTo("개발팀장");
        assertThat(detail.sourceMeetingTitle()).isEqualTo("기획 회의");
        assertThat(detail.sourceMeetingScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    void getTeamActionDetailLeavesAssigneeNullWhenTeamLeaderIsVacant() {
        TeamActionService service = teamActionService();
        Action action = teamAction(10L, ActionStatus.IN_PROGRESS);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, null, "GOODS", "굿즈")));
        when(actionReferenceRepository.findTeamReferences(List.of(TEAM)))
                .thenReturn(List.of(new TeamReference(TEAM, "개발팀", null)));
        when(actionReferenceRepository.findProjectAttachments(PROJECT)).thenReturn(List.of());

        TeamActionDetail detail = service.getTeamActionDetail(COMPANY, 10L);

        assertThat(detail.assigneeName()).isNull();
        assertThat(detail.assigneeRoleLabel()).isNull();
        assertThat(detail.sourceMeetingTitle()).isNull();
        assertThat(detail.sourceMeetingScheduledAt()).isNull();
        verify(actionReferenceRepository, never()).findMemberReferences(anyList());
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
        when(actionRepository.findAllByParentActionId(COMPANY, 10L)).thenReturn(List.of(child));
        when(actionReferenceRepository.findMemberReferences(List.of(5L)))
                .thenReturn(List.of(new MemberReference(5L, "이태연", null)));

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
        when(actionRepository.findAllByParentActionId(COMPANY, 10L)).thenReturn(List.of());

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

    // ── 2026-08-10 첨부파일 다운로드 URL 발급 ──────────────────────

    @Test
    void issueAttachmentDownloadUrlReturnsUrlWhenAttachmentBelongsToActionsProject() {
        TeamActionService service = teamActionService();
        Action action = teamAction(10L, ActionStatus.IN_PROGRESS);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));
        when(actionReferenceRepository.findProjectAttachmentById(1L, PROJECT))
                .thenReturn(Optional.of(new AttachmentReference(1L, "기획서.pdf", "https://s3/x", 1024L, LocalDateTime.now())));
        when(projectAttachmentStoragePort.issueDownloadUrl("https://s3/x"))
                .thenReturn(new IssuedDownloadUrl("https://s3/get", 300));

        IssuedDownloadUrl result = service.issueAttachmentDownloadUrl(COMPANY, 10L, 1L);

        assertThat(result.downloadUrl()).isEqualTo("https://s3/get");
        assertThat(result.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    void issueAttachmentDownloadUrlThrowsNotFoundWhenAttachmentBelongsToAnotherProject() {
        TeamActionService service = teamActionService();
        Action action = teamAction(10L, ActionStatus.IN_PROGRESS);
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));
        when(actionReferenceRepository.findProjectAttachmentById(1L, PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueAttachmentDownloadUrl(COMPANY, 10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_ATTACHMENT_NOT_FOUND);
    }

    @Test
    void issueAttachmentDownloadUrlThrowsNotFoundWhenTeamActionBelongsToAnotherCompany() {
        TeamActionService service = teamActionService();
        Action action = Action.reconstitute(
                10L, 999L, PROJECT, null, null, TEAM, null,
                ActionType.TEAM, "팀 액션", "설명", false, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
        when(actionRepository.findById(10L)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.issueAttachmentDownloadUrl(COMPANY, 10L, 1L))
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
