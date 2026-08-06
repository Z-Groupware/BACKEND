package com.module06.backend.identity.team.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

@SpringBootTest
@DisplayName("Team 영속성 어댑터")
class TeamPersistenceAdapterTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SpringDataTeamRepository springDataTeamRepository;

    @BeforeEach
    void clear() {
        springDataTeamRepository.deleteAll();
    }

    @Test
    @DisplayName("생성한 팀을 회사·id로 다시 찾을 수 있다")
    void createsAndFindsByIdAndCompanyId() {
        Team created = teamRepository.create(1L, null, "본부");

        Optional<Team> found = teamRepository.findByIdAndCompanyId(created.id(), 1L);

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("본부");
        assertThat(found.get().parentTeamId()).isNull();
    }

    @Test
    @DisplayName("다른 회사 id로는 찾지 못한다")
    void doesNotFindAcrossCompanies() {
        Team created = teamRepository.create(1L, null, "본부");

        assertThat(teamRepository.findByIdAndCompanyId(created.id(), 999L)).isEmpty();
    }

    @Test
    @DisplayName("회사의 전체 팀을 flat 하게 조회한다")
    void findsAllTeamsByCompany() {
        teamRepository.create(1L, null, "본부A");
        Team parent = teamRepository.create(1L, null, "본부B");
        teamRepository.create(1L, parent.id(), "하위팀");
        teamRepository.create(2L, null, "다른회사팀");

        List<Team> teams = teamRepository.findByCompanyId(1L);

        assertThat(teams).hasSize(3);
    }

    @Test
    @DisplayName("이름을 수정하면 다시 조회했을 때 반영돼 있다")
    void renamesTeam() {
        Team created = teamRepository.create(1L, null, "본부");

        teamRepository.rename(created.id(), "새이름");

        assertThat(teamRepository.findByIdAndCompanyId(created.id(), 1L).get().name())
                .isEqualTo("새이름");
    }

    @Test
    @DisplayName("삭제하면 더 이상 조회되지 않는다")
    void deletesTeam() {
        Team created = teamRepository.create(1L, null, "본부");

        teamRepository.delete(created.id());

        assertThat(teamRepository.findByIdAndCompanyId(created.id(), 1L)).isEmpty();
    }

    @Test
    @DisplayName("같은 부모 안 이름 중복을 감지한다 — 최상위(parent=null) 스코프")
    void detectsDuplicateNameAmongTopLevelSiblings() {
        teamRepository.create(1L, null, "본부");

        assertThat(teamRepository.existsByCompanyIdAndParentTeamIdIsNullAndName(1L, "본부")).isTrue();
        assertThat(teamRepository.existsByCompanyIdAndParentTeamIdIsNullAndName(1L, "없음")).isFalse();
    }

    @Test
    @DisplayName("같은 부모 안 이름 중복을 감지한다 — 하위 팀 스코프")
    void detectsDuplicateNameAmongChildSiblings() {
        Team parent = teamRepository.create(1L, null, "본부");
        teamRepository.create(1L, parent.id(), "1팀");

        assertThat(teamRepository.existsByCompanyIdAndParentTeamIdAndName(1L, parent.id(), "1팀")).isTrue();
        assertThat(teamRepository.existsByCompanyIdAndParentTeamIdAndName(1L, parent.id(), "2팀")).isFalse();
    }

    @Test
    @DisplayName("하위 팀이 있는지 확인한다")
    void detectsExistingChildren() {
        Team parent = teamRepository.create(1L, null, "본부");
        teamRepository.create(1L, parent.id(), "1팀");

        assertThat(teamRepository.existsByParentTeamId(parent.id())).isTrue();
        assertThat(teamRepository.existsByParentTeamId(999L)).isFalse();
    }
}
