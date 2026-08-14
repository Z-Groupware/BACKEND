package com.module06.backend.project.infrastructure.persistence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
    public List<Project> findAllByCompanyId(Long companyId, String keyword, ProjectStatus status, String sort, String order, int page, int size) {
        Specification<ProjectJpaEntity> specification = buildProjectSpecification(companyId, keyword, status);
        PageRequest pageRequest = PageRequest.of(page, size, buildProjectSort(sort, order));

        List<ProjectJpaEntity> entities = springDataProjectRepository.findAll(specification, pageRequest).getContent();
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
    public long countByCompanyId(Long companyId, String keyword, ProjectStatus status) {
        return springDataProjectRepository.count(buildProjectSpecification(companyId, keyword, status));
    }

    // 2026-08-11 — 오너 대시보드 KPI "마감 D-7" 카드. buildProjectSpecification을 그대로 쓰지
    // 않는 이유는 그쪽이 status 단일값 필터라 "DONE 제외"(!=)를 표현 못 하기 때문이다.
    @Override
    public long countDueSoonByCompanyId(Long companyId, LocalDate from, LocalDate to) {
        Specification<ProjectJpaEntity> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("companyId"), companyId),
                cb.isNull(root.get("deletedAt")),
                cb.notEqual(root.get("status"), ProjectStatus.DONE),
                cb.between(root.get("dueDate"), from, to)
        );

        return springDataProjectRepository.count(specification);
    }

    // 2026-08-10 필터/정렬 도입(이홍근 요청) — status가 null이면 조건에서 빠진다(필터 안 함).
    // content 쿼리와 count 쿼리가 항상 같은 조건을 쓰도록 이 메서드 하나로 통일한다
    // (totalElements가 필터링 전 기준이면 화면이 거짓말을 하게 된다).
    // 2026-08-13 keyword 검색 추가 — null/빈문자열이면 조건에서 빠진다. name에 대소문자 무시 LIKE.
    // 2026-08-14(코드래빗 지적, PR #499) — status는 이제 조회 시점에 재계산되는 파생값이라
    // (Project.deriveStatus, 이슈 #497), 저장된 raw 컬럼을 그대로 비교하면 아무도 재저장하지
    // 않은 행이 필터에서 빠지거나 잘못 잡힌다. Project.deriveStatus와 같은 조건을 SQL로도 맞춘다.
    private Specification<ProjectJpaEntity> buildProjectSpecification(Long companyId, String keyword, ProjectStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(buildDerivedStatusPredicate(root, cb, status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate buildDerivedStatusPredicate(Root<ProjectJpaEntity> root, CriteriaBuilder cb, ProjectStatus status) {
        LocalDate today = LocalDate.now();
        return switch (status) {
            case DONE -> cb.equal(root.get("status"), ProjectStatus.DONE);
            case IN_PROGRESS -> cb.and(
                    cb.notEqual(root.get("status"), ProjectStatus.DONE),
                    cb.isNotNull(root.get("startDate")),
                    cb.lessThanOrEqualTo(root.get("startDate"), today)
            );
            case TODO -> cb.and(
                    cb.notEqual(root.get("status"), ProjectStatus.DONE),
                    cb.or(
                            cb.isNull(root.get("startDate")),
                            cb.greaterThan(root.get("startDate"), today)
                    )
            );
        };
    }

    // 정렬 화이트리스트 — dueDate·createdAt·name만 허용. 그 외 값(오타·미지원 필드)은 기본 정렬로 조용히
    // 대체한다(400 대신) — 목록 화면이 정렬 파라미터 하나 때문에 통째로 깨지는 것을 막기 위함.
    private Sort buildProjectSort(String sort, String order) {
        String field = switch (sort == null ? "" : sort) {
            case "dueDate" -> "dueDate";
            case "createdAt" -> "createdAt";
            case "name" -> "name";
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // id를 보조 정렬키로 덧붙인다 — 같은 dueDate·createdAt을 가진 행이 여러 개면 DB가 순서를
        // 보장 안 해서 페이지 경계에서 중복/누락이 생길 수 있다(CodeRabbit 지적, PR #305).
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
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
