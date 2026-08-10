package com.module06.backend.project.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.project.domain.model.Project;

/* comment.
    프로젝트 저장소 계약. domain이 선언하고 infrastructure(ProjectPersistenceAdapter)가 구현한다.
*/
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(Long id);

    boolean existsByTag(String tag);

    // 2026-08-10 페이지네이션 도입(이홍근 요청) — page는 0부터 시작.
    List<Project> findAllByCompanyId(Long companyId, int page, int size);

    long countByCompanyId(Long companyId);

    boolean existsActiveByCompanyIdAndId(Long companyId, Long id);

    List<Project> findAllByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    List<Project> findAllByCompanyIdAndCreatedBy(Long companyId, Long createdBy);
}
