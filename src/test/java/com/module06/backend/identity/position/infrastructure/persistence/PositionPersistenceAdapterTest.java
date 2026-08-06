package com.module06.backend.identity.position.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;

@SpringBootTest
@Transactional
@DisplayName("Position 영속성 어댑터")
class PositionPersistenceAdapterTest {

    @Autowired
    private PositionRepository positionRepository;

    @Test
    @DisplayName("생성한 직급을 회사·id로 다시 찾을 수 있다")
    void createsAndFindsByIdAndCompanyId() {
        Position created = positionRepository.create(1L, "사원", Authority.MEMBER, "설명");

        Optional<Position> found = positionRepository.findByIdAndCompanyId(created.id(), 1L);

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("사원");
        assertThat(found.get().authority()).isEqualTo(Authority.MEMBER);
        assertThat(found.get().description()).isEqualTo("설명");
    }

    @Test
    @DisplayName("다른 회사 id로는 찾지 못한다")
    void doesNotFindAcrossCompanies() {
        Position created = positionRepository.create(1L, "사원", Authority.MEMBER, "설명");

        assertThat(positionRepository.findByIdAndCompanyId(created.id(), 999L)).isEmpty();
    }

    @Test
    @DisplayName("회사의 전체 직급을 조회한다")
    void findsAllPositionsByCompany() {
        positionRepository.create(1L, "사원", Authority.MEMBER, "설명");
        positionRepository.create(1L, "대리", Authority.MEMBER, "설명");
        positionRepository.create(2L, "다른회사직급", Authority.MEMBER, "설명");

        List<Position> positions = positionRepository.findByCompanyId(1L);

        assertThat(positions).hasSize(2);
    }

    @Test
    @DisplayName("이름·권한·설명을 수정하면 다시 조회했을 때 반영돼 있다")
    void updatesPosition() {
        Position created = positionRepository.create(1L, "사원", Authority.MEMBER, "설명");

        positionRepository.update(created.id(), "대리", Authority.LEADER, "새 설명");

        Position updated = positionRepository.findByIdAndCompanyId(created.id(), 1L).get();
        assertThat(updated.name()).isEqualTo("대리");
        assertThat(updated.authority()).isEqualTo(Authority.LEADER);
        assertThat(updated.description()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("삭제하면 더 이상 조회되지 않는다")
    void deletesPosition() {
        Position created = positionRepository.create(1L, "사원", Authority.MEMBER, "설명");

        positionRepository.delete(created.id());

        assertThat(positionRepository.findByIdAndCompanyId(created.id(), 1L)).isEmpty();
    }

    @Test
    @DisplayName("같은 회사 안 이름 중복을 감지한다")
    void detectsDuplicateNameInSameCompany() {
        positionRepository.create(1L, "사원", Authority.MEMBER, "설명");

        assertThat(positionRepository.existsByCompanyIdAndName(1L, "사원")).isTrue();
        assertThat(positionRepository.existsByCompanyIdAndName(1L, "없음")).isFalse();
    }

    @Test
    @DisplayName("다른 회사의 같은 이름은 중복이 아니다")
    void doesNotTreatOtherCompanyNameAsDuplicate() {
        positionRepository.create(1L, "사원", Authority.MEMBER, "설명");

        assertThat(positionRepository.existsByCompanyIdAndName(2L, "사원")).isFalse();
    }
}
