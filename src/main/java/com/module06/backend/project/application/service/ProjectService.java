package com.module06.backend.project.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.command.BulkUpdateProjectStatusCommand;
import com.module06.backend.project.application.command.CreateProjectCommand;
import com.module06.backend.project.application.command.UpdateProjectCommand;
import com.module06.backend.project.application.policy.ProjectOwnerOnlyPolicy;
import com.module06.backend.project.application.policy.ProjectTeamOwnershipPolicy;
import com.module06.backend.project.application.usecase.BulkUpdateProjectStatusUseCase;
import com.module06.backend.project.application.usecase.CreateProjectUseCase;
import com.module06.backend.project.application.usecase.GetProjectDetailUseCase;
import com.module06.backend.project.application.usecase.GetProjectListUseCase;
import com.module06.backend.project.application.usecase.GetProjectTimelineUseCase;
import com.module06.backend.project.application.usecase.UpdateProjectUseCase;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;
import com.module06.backend.project.domain.repository.ProjectRepository;
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
        GetProjectTimelineUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectAttachmentRepository projectAttachmentRepository;
    private final ProjectOwnerOnlyPolicy projectOwnerOnlyPolicy;
    private final ProjectTeamOwnershipPolicy projectTeamOwnershipPolicy;

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
                command.dueDate(),
                command.createdBy(),
                command.teamIds()
        );

        return projectRepository.save(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> list(Long companyId) {
        return projectRepository.findAllByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResult getDetail(Long projectId) {
        Project project = projectRepository.findById(projectId)
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

        project.update(command.name(), command.description(), command.color(), command.dueDate(), command.teamIds());

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
                    project.changeStatus(item.status());
                    return project;
                })
                .toList();

        projects.forEach(projectRepository::save);
    }
}
