package com.module06.backend.project.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    project 테이블용 Spring Data JPA 인터페이스. 목록·타임라인 조회는 N+1 취약 지점이라
    필요해지면 fetch join/projection을 나중에 추가한다.
*/
public interface SpringDataProjectRepository extends JpaRepository<ProjectJpaEntity, Long> {

    boolean existsByTag(String tag);

    List<ProjectJpaEntity> findAllByCompanyId(Long companyId);
}
