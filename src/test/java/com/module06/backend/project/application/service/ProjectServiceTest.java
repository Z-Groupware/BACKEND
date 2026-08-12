package com.module06.backend.project.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.port.ActionQueryPort;
import com.module06.backend.action.application.port.ActionQueryPort.ProjectActionCount;
import com.module06.backend.action.application.port.ActionQueryPort.TeamActionSummary;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.command.BulkUpdateProjectStatusCommand;
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.command.UpdateProjectCommand;
import com.module06.backend.project.application.policy.ProjectOwnerOnlyPolicy;
import com.module06.backend.project.application.policy.ProjectTeamOwnershipPolicy;
import com.module06.backend.project.application.port.ProjectQueryPort.ProjectSummary;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase.ProjectDetailResult;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase.TimelineItem;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;
import com.module06.backend.project.domain.repository.ProjectRepository;
import com.module06.backend.project.domain.repository.TeamReferenceRepository;
import com.module06.backend.project.exception.ProjectErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long OTHER_COMPANY = 2L;
    private static final Long PROJECT_ID = 100L;
    private static final Long OWNER = 3L;
    private static final Long STRANGER = 4L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAttachmentRepository projectAttachmentRepository;

    @Mock
    private ProjectOwnerOnlyPolicy projectOwnerOnlyPolicy;

    @Mock
    private ProjectTeamOwnershipPolicy projectTeamOwnershipPolicy;

    @Mock
    private ActionQueryPort actionQueryPort;

    @Mock
    private com.module06.backend.project.application.port.MeetingQueryPort meetingQueryPort;

    @Mock
    private TeamReferenceRepository teamReferenceRepository;

    private ProjectService projectService;

    private ProjectService service() {
        return new ProjectService(projectRepository, projectAttachmentRepository,
                projectOwnerOnlyPolicy, projectTeamOwnershipPolicy, actionQueryPort, meetingQueryPort,
                teamReferenceRepository);
    }

    private Project project(Long companyId) {
        return Project.create(companyId, "TAG", "이름", "설명", "#16A34A",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), OWNER, List.of(1L, 2L));
    }

    // ---------- create ----------

    @Test
    void createSavesProjectWhenTagIsNotDuplicate() {
        projectService = service();
        when(projectRepository.existsByTag("TAG")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Project created = projectService.create(new CreateProjectCommand(
                COMPANY, OWNER, "TAG", "새 프로젝트", "설명", "#16A34A",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), List.of(1L, 2L)));

        verify(projectTeamOwnershipPolicy).check(List.of(1L, 2L), COMPANY);
        verify(projectRepository).save(any(Project.class));
        assertThat(created.getTag()).isEqualTo("TAG");
        assertThat(created.getCreatedBy()).isEqualTo(OWNER);
    }

    @Test
    void createThrowsWhenTagIsDuplicate() {
        projectService = service();
        when(projectRepository.existsByTag("TAG")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(new CreateProjectCommand(
                COMPANY, OWNER, "TAG", "새 프로젝트", "설명", "#16A34A",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), List.of(1L))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_TAG_DUPLICATE);

        verify(projectTeamOwnershipPolicy, never()).check(anyList(), any());
        verify(projectRepository, never()).save(any(Project.class));
    }

    // ---------- getOwnerDashboardSummary (이슈 #352) ----------

    // CodeRabbit 지적 반영 — 테스트의 LocalDate.now()와 서비스 내부의 LocalDate.now()는 서로
    // 다른 시점에 평가되는 독립 호출이라, 자정을 걸치면 두 값이 갈려 stub이 안 맞을 수 있다.
    // any(LocalDate.class)로 받고 인자를 캡처해 "이틀 사이 7일 창"이라는 관계만 검증한다.
    @Test
    void getOwnerDashboardSummaryReturnsTotalAndDueSoonProjectCounts() {
        projectService = service();
        when(projectRepository.countByCompanyId(COMPANY, null)).thenReturn(3L);
        when(projectRepository.countDueSoonByCompanyId(eq(COMPANY), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(1L);

        var result = projectService.getOwnerDashboardSummary(COMPANY);

        assertThat(result.totalProjectCount()).isEqualTo(3L);
        assertThat(result.dueSoonProjectCount()).isEqualTo(1L);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(projectRepository).countDueSoonByCompanyId(eq(COMPANY), from.capture(), to.capture());
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(7));
    }

    @Test
    void getOwnerDashboardSummaryReturnsZerosWhenCompanyHasNoProjects() {
        projectService = service();
        when(projectRepository.countByCompanyId(COMPANY, null)).thenReturn(0L);
        when(projectRepository.countDueSoonByCompanyId(eq(COMPANY), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);

        var result = projectService.getOwnerDashboardSummary(COMPANY);

        assertThat(result.totalProjectCount()).isZero();
        assertThat(result.dueSoonProjectCount()).isZero();
    }

    // ---------- list ----------

    @Test
    void listReturnsAllProjectsWithZeroCountsWhenNoActionsOrMeetings() {
        projectService = service();
        Project project = Project.reconstitute(1L, COMPANY, "TAG", "이름", "설명", "#16A34A",
                ProjectStatus.TODO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), OWNER, List.of(1L, 2L), null, null, null);
        when(projectRepository.findAllByCompanyId(COMPANY, null, null, "desc", 0, 20)).thenReturn(List.of(project));
        when(projectRepository.countByCompanyId(COMPANY, null)).thenReturn(1L);
        when(actionQueryPort.countActionsByProjectIds(any())).thenReturn(List.of());
        when(meetingQueryPort.countMeetingsByProjectIds(eq(COMPANY), any())).thenReturn(Map.of());
        when(teamReferenceRepository.findTeamNames(any(), eq(COMPANY))).thenReturn(List.of(
                new TeamReferenceRepository.TeamName(1L, "개발팀"),
                new TeamReferenceRepository.TeamName(2L, "마케팅팀")));

        GetProjectListUseCase.ProjectListResult result = projectService.list(COMPANY, null, null, "desc", 0, 20);

        assertThat(result.items()).containsExactly(
                new GetProjectListUseCase.ProjectListItem(project, 0, 0, 0, List.of("개발팀", "마케팅팀")));
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listAttachesActionAndMeetingCountsFromBatchQueries() {
        projectService = service();
        Project projectA = Project.reconstitute(1L, COMPANY, "TAG-A", "A", "설명", "#000000",
                ProjectStatus.TODO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), OWNER, List.of(), null, null, null);
        Project projectB = Project.reconstitute(2L, COMPANY, "TAG-B", "B", "설명", "#000000",
                ProjectStatus.TODO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), OWNER, List.of(), null, null, null);
        when(projectRepository.findAllByCompanyId(COMPANY, null, null, "desc", 0, 20)).thenReturn(List.of(projectA, projectB));
        when(projectRepository.countByCompanyId(COMPANY, null)).thenReturn(2L);
        when(actionQueryPort.countActionsByProjectIds(any())).thenReturn(List.of(
                new ProjectActionCount(1L, 5, 2)));
        when(meetingQueryPort.countMeetingsByProjectIds(eq(COMPANY), any())).thenReturn(Map.of(1L, 3L));
        when(teamReferenceRepository.findTeamNames(any(), eq(COMPANY))).thenReturn(List.of());

        GetProjectListUseCase.ProjectListResult result = projectService.list(COMPANY, null, null, "desc", 0, 20);

        assertThat(result.items()).containsExactly(
                new GetProjectListUseCase.ProjectListItem(projectA, 5, 2, 3, List.of()),
                new GetProjectListUseCase.ProjectListItem(projectB, 0, 0, 0, List.of()));
        assertThat(result.totalElements()).isEqualTo(2L);
    }

    // ---------- getDetail ----------

    @Test
    void getDetailReturnsProjectAndAttachmentsWhenSameCompany() {
        projectService = service();
        Project project = project(COMPANY);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectAttachmentRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of());

        ProjectDetailResult result = projectService.getDetail(COMPANY, PROJECT_ID);

        assertThat(result.project()).isEqualTo(project);
        assertThat(result.attachments()).isEmpty();
    }

    @Test
    void getDetailThrowsWhenProjectNotFound() {
        projectService = service();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getDetail(COMPANY, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void getDetailThrowsWhenProjectBelongsToAnotherCompany() {
        projectService = service();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(OTHER_COMPANY)));

        assertThatThrownBy(() -> projectService.getDetail(COMPANY, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);
    }

    // ---------- update ----------

    @Test
    void updateAppliesChangesWhenRequesterIsOwner() {
        projectService = service();
        Project project = project(COMPANY);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Project updated = projectService.update(new UpdateProjectCommand(
                PROJECT_ID, OWNER, "새 이름", "새 설명", "#000000", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1), List.of(5L)
        ));

        verify(projectOwnerOnlyPolicy).check(project, OWNER);
        verify(projectTeamOwnershipPolicy).check(List.of(5L), COMPANY);
        assertThat(updated.getName()).isEqualTo("새 이름");
        assertThat(updated.getTeamIds()).containsExactly(5L);
    }

    @Test
    void updateThrowsWhenProjectNotFound() {
        projectService = service();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.update(
                new UpdateProjectCommand(PROJECT_ID, OWNER, "이름", "설명", "#000000", LocalDate.now(), LocalDate.now(), List.of())
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void updateThrowsWhenRequesterIsNotOwner() {
        projectService = service();
        Project project = project(COMPANY);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        org.mockito.Mockito.doThrow(new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER))
                .when(projectOwnerOnlyPolicy).check(project, STRANGER);

        assertThatThrownBy(() -> projectService.update(
                new UpdateProjectCommand(PROJECT_ID, STRANGER, "이름", "설명", "#000000", LocalDate.now(), LocalDate.now(), List.of())
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.NOT_PROJECT_OWNER);
    }

    // ---------- bulkUpdateStatus ----------

    @Test
    void bulkUpdateStatusSavesAllItemsWhenAllOwnedByRequester() {
        projectService = service();
        Project projectA = project(COMPANY);
        Project projectB = project(COMPANY);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(projectB));

        projectService.bulkUpdateStatus(new BulkUpdateProjectStatusCommand(OWNER, List.of(
                new BulkUpdateProjectStatusCommand.Item(1L, ProjectStatus.IN_PROGRESS),
                new BulkUpdateProjectStatusCommand.Item(2L, ProjectStatus.DONE)
        )));

        assertThat(projectA.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(projectB.getStatus()).isEqualTo(ProjectStatus.DONE);
        verify(projectRepository).save(projectA);
        verify(projectRepository).save(projectB);
    }

    @Test
    void bulkUpdateStatusSavesNothingWhenAnyItemFailsOwnerCheck() {
        projectService = service();
        Project projectA = project(COMPANY);
        Project projectB = project(COMPANY);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectA));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(projectB));
        org.mockito.Mockito.doAnswer(invocation -> {
            Project checked = invocation.getArgument(0);
            if (checked == projectB) {
                throw new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER);
            }
            return null;
        }).when(projectOwnerOnlyPolicy).check(any(Project.class), eq(OWNER));

        assertThatThrownBy(() -> projectService.bulkUpdateStatus(new BulkUpdateProjectStatusCommand(OWNER, List.of(
                new BulkUpdateProjectStatusCommand.Item(1L, ProjectStatus.IN_PROGRESS),
                new BulkUpdateProjectStatusCommand.Item(2L, ProjectStatus.DONE)
        )))).isInstanceOf(BusinessException.class);

        verify(projectRepository, never()).save(any(Project.class));
    }

    // ---------- getTimeline ----------

    @Test
    void getTimelineMarksOverdueIncompleteActionsAsDelayed() {
        projectService = service();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(COMPANY)));
        when(actionQueryPort.findTeamActionsByProjectId(PROJECT_ID)).thenReturn(List.of(
                new TeamActionSummary(10L, "지연된 팀 액션", 1L, "개발팀", ActionStatus.IN_PROGRESS, LocalDate.of(2020, 1, 1)),
                new TeamActionSummary(11L, "완료된 팀 액션", 1L, "개발팀", ActionStatus.DONE, LocalDate.of(2020, 1, 1)),
                new TeamActionSummary(12L, "예정된 팀 액션", 1L, "개발팀", ActionStatus.TODO, LocalDate.of(2099, 1, 1))
        ));

        List<TimelineItem> timeline = projectService.getTimeline(COMPANY, PROJECT_ID);

        assertThat(timeline).extracting(TimelineItem::actionId, TimelineItem::isDelayed)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(10L, true),
                        org.assertj.core.groups.Tuple.tuple(11L, false),
                        org.assertj.core.groups.Tuple.tuple(12L, false)
                );
    }

    @Test
    void getTimelineThrowsWhenProjectBelongsToAnotherCompany() {
        projectService = service();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(OTHER_COMPANY)));

        assertThatThrownBy(() -> projectService.getTimeline(COMPANY, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);
    }

    // ---------- ProjectQueryPort (meeting(D)이 호출) ----------

    @Test
    void existsActiveProjectDelegatesToRepository() {
        projectService = service();
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);

        assertThat(projectService.existsActiveProject(COMPANY, PROJECT_ID)).isTrue();
    }

    @Test
    void findProjectsMapsToProjectSummary() {
        projectService = service();
        Project project = project(COMPANY);
        when(projectRepository.findAllByCompanyIdAndIdIn(COMPANY, List.of(PROJECT_ID))).thenReturn(List.of(project));

        List<ProjectSummary> result = projectService.findProjects(COMPANY, List.of(PROJECT_ID));

        assertThat(result).containsExactly(
                new ProjectSummary(project.getId(), project.getTag(), project.getName(), project.getColor()));
    }
}
