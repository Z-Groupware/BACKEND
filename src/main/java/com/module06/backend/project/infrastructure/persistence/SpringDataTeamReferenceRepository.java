package com.module06.backend.project.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    team 테이블 읽기 전용 조회. 회사 소속 검증(팀 id 목록이 전부 이 회사 소속인지)에 쓴다.
*/
public interface SpringDataTeamReferenceRepository extends JpaRepository<TeamReferenceEntity, Long> {

    List<TeamReferenceEntity> findAllByIdInAndCompanyId(List<Long> ids, Long companyId);
}
