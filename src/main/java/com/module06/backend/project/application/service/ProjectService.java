package com.module06.backend.project.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.port.ActionQueryPort;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.command.BulkUpdateProjectStatusCommand;
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.command.UpdateProjectCommand;
import com.module06.backend.project.application.policy.ProjectOwnerOnlyPolicy;
import com.module06.backend.project.application.policy.ProjectTeamOwnershipPolicy;
import com.module06.backend.project.application.port.MeetingQueryPort;
import com.module06.backend.project.application.port.ProjectQueryPort;
import com.module06.backend.project.application.usecase.BulkUpdateProjectStatusUseCase;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetOwnerDashboardSummaryUseCase;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase;
import com.module06.backend.project.application.usecase.UpdateProjectUseCase;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;
import com.module06.backend.project.domain.repository.ProjectRepository;
import com.module06.backend.project.domain.repository.TeamReferenceRepository;
import com.module06.backend.project.exception.ProjectErrorCode;

import lombok.RequiredArgsConstructor;

/* comment.
    프로젝트 리소스(FR-PJ-01,02,03,06,07)를 다루는 단일 구현체 — 08/04 팀 협의(윤종호)로
    UseCase별 서비스 파편화 대신 이 클래스 하나로 묶기로 확정. 메서드는 UseCase 인터페이스가
    실로직 단계에서 채워질 때마다 하나씩 추가된다.
*/
@Service
@RequiredArgsConstructor
public class ProjectService implements
        CreateProjectUseCase,
        UpdateProjectUseCase,
        GetProjectListUseCase,
        GetProjectDetailUseCase,
        BulkUpdateProjectStatusUseCase,
        GetProjectTimelineUseCase,
        GetOwnerDashboardSummaryUseCase,
        ProjectQueryPort {

    private final ProjectRepository projectRepository;
    private final ProjectAttachmentRepository projectAttachmentRepository;
    private final ProjectOwnerOnlyPolicy projectOwnerOnlyPolicy;
    private final ProjectTeamOwnershipPolicy projectTeamOwnershipPolicy;
    private final ActionQueryPort actionQueryPort;
    private final MeetingQueryPort meetingQueryPort;
    private final TeamReferenceRepository teamReferenceRepository;

    @Override
    @Transactional
    public Project create(CreateProjectCommand command) {
        if (projectRepository.existsByTag(command.tag())) {
            throw new BusinessException(ProjectErrorCode.PROJECT_TAG_DUPLICATE);
        }

        projectTeamOwnershipPolicy.check(command.teamIds(), command.companyId());

        Project project = Project.create(
                command.companyId(),
                command.tag(),
                command.name(),
                command.description(),
                command.color(),
                command.startDate(),
                command.dueDate(),
                command.createdBy(),
                command.teamIds()
        );

        return projectRepository.save(project);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectListResult list(Long companyId, String keyword, ProjectStatus status, String sort, String order, int page, int size) {
        List<Project> projects = projectRepository.findAllByCompanyId(companyId, keyword, status, sort, order, page, size);
        long totalElements = projectRepository.countByCompanyId(companyId, keyword, status);
        List<Long> projectIds = projects.stream().map(Project::getId).toList();

        Map<Long, ActionQueryPort.ProjectActionCount> countsByProjectId =
                actionQueryPort.countActionsByProjectIds(companyId, projectIds).stream()
                        .collect(Collectors.toMap(ActionQueryPort.ProjectActionCount::projectId, count -> count));
        Map<Long, Long> meetingCountByProjectId = meetingQueryPort.countMeetingsByProjectIds(companyId, projectIds);

        // 부서 칩 표시용 이름 — 이 페이지 프로젝트의 teamIds를 한 번에 모아 배치 조회한다(N+1 방지).
        List<Long> allTeamIds = projects.stream()
                .flatMap(project -> project.getTeamIds().stream())
                .distinct()
                .toList();
        Map<Long, String> teamNameById = teamReferenceRepository.findTeamNames(allTeamIds, companyId).stream()
                .collect(Collectors.toMap(TeamReferenceRepository.TeamName::id, TeamReferenceRepository.TeamName::name));

        List<ProjectListItem> items = projects.stream()
                .map(project -> {
                    ActionQueryPort.ProjectActionCount count = countsByProjectId.get(project.getId());
                    int meetingCount = Math.toIntExact(meetingCountByProjectId.getOrDefault(project.getId(), 0L));
                    List<String> teamNames = project.getTeamIds().stream().map(teamNameById::get).toList();
                    return count == null
                            ? new ProjectListItem(project, 0, 0, meetingCount, teamNames)
                            : new ProjectListItem(project, count.totalCount(), count.completedCount(), meetingCount, teamNames);
                })
                .toList();

        return new ProjectListResult(items, totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResult getDetail(Long companyId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .filter(found -> found.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        List<ProjectAttachment> attachments = projectAttachmentRepository.findAllByProjectId(projectId);

        return new ProjectDetailResult(project, attachments);
    }

    @Override
    @Transactional
    public Project update(UpdateProjectCommand command) {
        Project project = projectRepository.findById(command.projectId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        projectOwnerOnlyPolicy.check(project, command.requesterId());
        projectTeamOwnershipPolicy.check(command.teamIds(), project.getCompanyId());

        project.update(command.name(), command.description(), command.color(), command.startDate(), command.dueDate(), command.teamIds());

        return projectRepository.save(project);
    }

    @Override
    @Transactional
    public void bulkUpdateStatus(BulkUpdateProjectStatusCommand command) {
        List<Project> projects = command.items().stream()
                .map(item -> {
                    Project project = projectRepository.findById(item.projectId())
                            .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
                    projectOwnerOnlyPolicy.check(project, command.requesterId());
                    project.changeStatus(item.status(), LocalDate.now());
                    return project;
                })
                .toList();

        projects.forEach(projectRepository::save);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimelineItem> getTimeline(Long companyId, Long projectId) {
        projectRepository.findById(projectId)
                .filter(found -> found.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        LocalDate today = LocalDate.now();

        return actionQueryPort.findTeamActionsByProjectId(projectId).stream()
                .map(summary -> new TimelineItem(
                        summary.actionId(),
                        summary.title(),
                        summary.teamId(),
                        summary.teamName(),
                        summary.status(),
                        summary.dueDate(),
                        summary.isDelayed(today)
                ))
                .toList();
    }

    // meeting(D)이 회의 개설 시 프로젝트가 활성 상태로 존재하는지 확인한다.
    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveProject(Long companyId, Long projectId) {
        return projectRepository.existsActiveByCompanyIdAndId(companyId, projectId);
    }

    // meeting(D)이 예정 회의 목록에 표시할 프로젝트 정보를 배치 조회한다(soft-delete 포함).
    @Override
    @Transactional(readOnly = true)
    public List<ProjectSummary> findProjects(Long companyId, List<Long> projectIds) {
        return projectRepository.findAllByCompanyIdAndIdIn(companyId, projectIds).stream()
                .map(project -> new ProjectSummary(project.getId(), project.getTag(), project.getName(), project.getColor()))
                .toList();
    }

    // 2026-08-11 — 오너 대시보드 KPI. "마감 D-7"은 오늘부터 7일 뒤(포함)까지를 창으로 잡는다
    // (이슈 #352 — 화면 라벨 "마감 D-7"과 동일한 의미로 이홍근 확인 전 임시 정의, PR 리뷰에서 확정).
    @Override
    @Transactional(readOnly = true)
    public OwnerDashboardSummary getOwnerDashboardSummary(Long companyId) {
        LocalDate today = LocalDate.now();
        long totalProjectCount = projectRepository.countByCompanyId(companyId, null, null);
        long dueSoonProjectCount = projectRepository.countDueSoonByCompanyId(companyId, today, today.plusDays(7));

        return new OwnerDashboardSummary(totalProjectCount, dueSoonProjectCount);
    }
}
