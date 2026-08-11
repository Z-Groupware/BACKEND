package com.module06.backend.project.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;

/* comment.
    프로젝트 저장소 계약. domain이 선언하고 infrastructure(ProjectPersistenceAdapter)가 구현한다.
*/
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(Long id);

    boolean existsByTag(String tag);

    // 2026-08-10 페이지네이션+필터+정렬 도입(이홍근 요청) — page는 0부터 시작.
    // status는 null이면 필터 안 함. sort/order는 컨트롤러가 화이트리스트로 정제한 값만 넘긴다.
    List<Project> findAllByCompanyId(Long companyId, ProjectStatus status, String sort, String order, int page, int size);

    // totalElements는 항상 같은 필터 기준이어야 한다 — page가 뭐든 필터링된 전체 건수를 반환.
    long countByCompanyId(Long companyId, ProjectStatus status);

    boolean existsActiveByCompanyIdAndId(Long companyId, Long id);

    List<Project> findAllByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    List<Project> findAllByCompanyIdAndCreatedBy(Long companyId, Long createdBy);

    // 2026-08-11 — 오너 대시보드 KPI "마감 D-7" 카드. 완료(DONE)된 프로젝트는 마감이 임박해도
    // "즉시 확인 필요"가 아니므로 제외한다 — countByCompanyId처럼 status를 열어두지 않고 DONE
    // 제외를 어댑터가 고정한다(이 카드 전용 집계라 호출측이 status를 고를 이유가 없다).
    long countDueSoonByCompanyId(Long companyId, LocalDate from, LocalDate to);
}
