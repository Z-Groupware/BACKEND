package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;

/*
 * access-guard의 프로젝트 멤버 판정이 기대는 project_team 조회(CapProjectTeamReferenceEntity)가
 * 실제 테이블 행과 맞게 동작하는지 검증한다. project_team은 project/team 테이블과 FK로 묶여 있지만
 * (V1__init_schema.sql), 이 어댑터의 엔티티는 project/team에 @ManyToOne을 맺지 않는 읽기 전용
 * 참조라 테스트 스키마(Hibernate create-drop)에는 그 FK가 생성되지 않는다 — project_team 행만
 * 직접 시딩한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP 프로젝트-팀 참조 어댑터")
class ProjectTeamReferenceRepositoryAdapterTest {

    @Autowired
    private ProjectTeamReferenceRepository projectTeamReferenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM project_team");
    }

    // created_at/updated_at은 DB 기본값이 아니라 Hibernate(@CreationTimestamp/@UpdateTimestamp)가
    // 채우는 값이라, JPA를 거치지 않는 이 raw insert에서는 직접 넣어야 한다.
    private void seed(Long projectId, Long teamId) {
        jdbcTemplate.update("""
                INSERT INTO project_team (project_id, team_id, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, projectId, teamId);
    }

    /* 배정된 (project_id, team_id) 조합만 true를 반환하는지 검증한다. */
    @Test
    @DisplayName("배정된 프로젝트-팀 조합만 true를 반환한다")
    void trueOnlyForAssignedPair() {
        seed(12L, 9L);

        assertThat(projectTeamReferenceRepository.isTeamAssignedToProject(12L, 9L)).isTrue();
        // 같은 프로젝트라도 다른 팀이면 false.
        assertThat(projectTeamReferenceRepository.isTeamAssignedToProject(12L, 99L)).isFalse();
        // 같은 팀이라도 다른 프로젝트면 false.
        assertThat(projectTeamReferenceRepository.isTeamAssignedToProject(999L, 9L)).isFalse();
    }

    /* 어떤 배정도 없으면 항상 false인지 검증한다. */
    @Test
    @DisplayName("배정이 전혀 없으면 false를 반환한다")
    void falseWhenNoAssignments() {
        assertThat(projectTeamReferenceRepository.isTeamAssignedToProject(12L, 9L)).isFalse();
    }
}
