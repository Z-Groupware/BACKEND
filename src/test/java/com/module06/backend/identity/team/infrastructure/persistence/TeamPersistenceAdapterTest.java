package com.module06.backend.identity.team.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

@SpringBootTest
@Transactional
@DisplayName("Team 영속성 어댑터")
class TeamPersistenceAdapterTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SpringDataTeamRepository springDataTeamRepository;

    @Test
    @DisplayName("생성한 팀을 회사·id로 다시 찾을 수 있다")
    void createsAndFindsByIdAndCompanyId() {
        Team created = teamRepository.create(1L, "본부");

        Optional<Team> found = teamRepository.findByIdAndCompanyId(created.id(), 1L);

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("본부");
    }

    @Test
    @DisplayName("다른 회사 id로는 찾지 못한다")
    void doesNotFindAcrossCompanies() {
        Team created = teamRepository.create(1L, "본부");

        assertThat(teamRepository.findByIdAndCompanyId(created.id(), 999L)).isEmpty();
    }

    @Test
    @DisplayName("회사의 전체 팀을 조회한다")
    void findsAllTeamsByCompany() {
        teamRepository.create(1L, "본부A");
        teamRepository.create(1L, "본부B");
        teamRepository.create(2L, "다른회사팀");

        List<Team> teams = teamRepository.findByCompanyId(1L);

        assertThat(teams).hasSize(2);
    }

    @Test
    @DisplayName("이름을 수정하면 다시 조회했을 때 반영돼 있다")
    void renamesTeam() {
        Team created = teamRepository.create(1L, "본부");

        teamRepository.rename(created.id(), "새이름");

        assertThat(teamRepository.findByIdAndCompanyId(created.id(), 1L).get().name())
                .isEqualTo("새이름");
    }

    @Test
    @DisplayName("삭제하면 더 이상 조회되지 않는다")
    void deletesTeam() {
        Team created = teamRepository.create(1L, "본부");

        teamRepository.delete(created.id());

        assertThat(teamRepository.findByIdAndCompanyId(created.id(), 1L)).isEmpty();
    }

    @Test
    @DisplayName("회사 안 이름 중복을 감지한다")
    void detectsDuplicateNameWithinCompany() {
        teamRepository.create(1L, "본부");

        assertThat(teamRepository.existsByCompanyIdAndName(1L, "본부")).isTrue();
        assertThat(teamRepository.existsByCompanyIdAndName(1L, "없음")).isFalse();
    }

    @Test
    @DisplayName("다른 회사의 같은 이름은 중복으로 보지 않는다")
    void doesNotTreatOtherCompanyNameAsDuplicate() {
        teamRepository.create(1L, "본부");

        assertThat(teamRepository.existsByCompanyIdAndName(2L, "본부")).isFalse();
    }
}
