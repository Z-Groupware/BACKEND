package com.module06.backend.cap.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// project_team 읽기 전용 리포지토리. ⚠️ Cap 접두어로 project 도메인의 동명 리포지토리와 빈 이름 충돌 회피.
public interface SpringDataCapProjectTeamReferenceRepository
        extends JpaRepository<CapProjectTeamReferenceEntity, CapProjectTeamId> {

    // 이 프로젝트에 이 팀이 배정돼 있는지 — access-guard의 프로젝트 멤버 판정(파생 쿼리, QUERY_002 준수).
    boolean existsByIdProjectIdAndIdTeamId(Long projectId, Long teamId);
}
