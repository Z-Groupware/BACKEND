package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TeamPersistenceAdapter implements TeamRepository {

    private final SpringDataTeamRepository repository;

    @Override
    public List<Team> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Team> findByIdAndCompanyId(Long id, Long companyId) {
        return repository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Team create(Long companyId, Long parentTeamId, String name) {
        TeamJpaEntity saved = repository.saveAndFlush(TeamJpaEntity.create(companyId, parentTeamId, name));
        return toDomain(saved);
    }

    /**
     * {@code @Transactional} 이 이 메서드 하나를 감싼다 — {@code findById} 가 주는 관리 상태(managed)가
     * dirty checking이 flush 되는 순간까지 살아 있어야 이름 변경이 실제로 반영된다. 호출자
     * (TeamService, Task 6)가 이미 트랜잭션 안이면 REQUIRED 전파로 그 트랜잭션에 합류하고,
     * 이 메서드를 단독으로 부르면(예: 이 태스크의 어댑터 테스트) 새 트랜잭션을 새로 연다 —
     * 두 경우 모두 find와 mutate가 같은 트랜잭션 안에 있어야 하는 이유가 같다.
     */
    @Override
    @Transactional
    public void rename(Long id, String name) {
        repository.findById(id).ifPresent(entity -> entity.rename(name));
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    public boolean existsByCompanyIdAndParentTeamIdAndName(Long companyId, Long parentTeamId, String name) {
        return repository.existsByCompanyIdAndParentTeamIdAndName(companyId, parentTeamId, name);
    }

    @Override
    public boolean existsByCompanyIdAndParentTeamIdIsNullAndName(Long companyId, String name) {
        return repository.existsByCompanyIdAndParentTeamIdIsNullAndName(companyId, name);
    }

    @Override
    public boolean existsByParentTeamId(Long parentTeamId) {
        return repository.existsByParentTeamId(parentTeamId);
    }

    private Team toDomain(TeamJpaEntity entity) {
        return new Team(entity.getId(), entity.getCompanyId(), entity.getName(),
                entity.getParentTeamId(), entity.getLeaderMemberId());
    }
}
