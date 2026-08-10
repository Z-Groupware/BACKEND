package com.module06.backend.project.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 ProjectRepository 계약을 JPA로 구현하는 어댑터. project_team은 ProjectJpaEntity와
    JPA 연관관계가 없어서, save/조회 시 SpringDataProjectTeamRepository를 별도로 조율한다.
*/
@Component
@RequiredArgsConstructor
public class ProjectPersistenceAdapter implements ProjectRepository {

    private final SpringDataProjectRepository springDataProjectRepository;
    private final SpringDataProjectTeamRepository springDataProjectTeamRepository;

    @Override
    public Project save(Project project) {
        ProjectJpaEntity entity = ProjectJpaEntity.builder()
                .id(project.getId())
                .companyId(project.getCompanyId())
                .tag(project.getTag())
                .name(project.getName())
                .description(project.getDescription())
                .color(project.getColor())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .dueDate(project.getDueDate())
                .createdBy(project.getCreatedBy())
                .deletedAt(project.getDeletedAt())
                .build();

        ProjectJpaEntity saved = springDataProjectRepository.save(entity);

        // 기존 지정 부서 싹 지우고 새로 저장 (delete-then-insert, 생성 시엔 지울 게 없어도 안전)
        springDataProjectTeamRepository.deleteAllById_ProjectId(saved.getId());
        List<ProjectTeamJpaEntity> teamEntities = project.getTeamIds().stream()
                .map(teamId -> new ProjectTeamJpaEntity(saved.getId(), teamId))
                .toList();
        springDataProjectTeamRepository.saveAll(teamEntities);

        return toDomain(saved, project.getTeamIds());
    }

    @Override
    public Optional<Project> findById(Long id) {
        return springDataProjectRepository.findById(id)
                .map(entity -> toDomain(entity, findTeamIds(id)));
    }

    @Override
    public boolean existsByTag(String tag) {
        return springDataProjectRepository.existsByTag(tag);
    }

    @Override
    public List<Project> findAllByCompanyId(Long companyId) {
        List<ProjectJpaEntity> entities = springDataProjectRepository.findAllByCompanyIdAndDeletedAtIsNull(companyId);
        List<Long> projectIds = entities.stream().map(ProjectJpaEntity::getId).toList();

        // N+1 방지 — 프로젝트별로 따로 조회하지 않고 한 번에 배치 조회해 프로젝트 id로 묶는다(2026-08-09).
        Map<Long, List<Long>> teamIdsByProjectId = springDataProjectTeamRepository.findAllById_ProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(
                        teamEntity -> teamEntity.getId().getProjectId(),
                        Collectors.mapping(teamEntity -> teamEntity.getId().getTeamId(), Collectors.toList())));

        return entities.stream()
                .map(entity -> toDomain(entity, teamIdsByProjectId.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    public boolean existsActiveByCompanyIdAndId(Long companyId, Long id) {
        return springDataProjectRepository.existsByCompanyIdAndIdAndDeletedAtIsNull(companyId, id);
    }

    @Override
    public List<Project> findAllByCompanyIdAndIdIn(Long companyId, List<Long> ids) {
        return springDataProjectRepository.findAllByCompanyIdAndIdIn(companyId, ids).stream()
                .map(entity -> toDomain(entity, findTeamIds(entity.getId())))
                .toList();
    }

    @Override
    public List<Project> findAllByCompanyIdAndCreatedBy(Long companyId, Long createdBy) {
        return springDataProjectRepository.findAllByCompanyIdAndCreatedByAndDeletedAtIsNull(companyId, createdBy).stream()
                .map(entity -> toDomain(entity, findTeamIds(entity.getId())))
                .toList();
    }

    private List<Long> findTeamIds(Long projectId) {
        return springDataProjectTeamRepository.findAllById_ProjectId(projectId).stream()
                .map(teamEntity -> teamEntity.getId().getTeamId())
                .toList();
    }

    private Project toDomain(ProjectJpaEntity entity, List<Long> teamIds) {
        return Project.reconstitute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getTag(),
                entity.getName(),
                entity.getDescription(),
                entity.getColor(),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getDueDate(),
                entity.getCreatedBy(),
                teamIds,
                entity.getDeletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
