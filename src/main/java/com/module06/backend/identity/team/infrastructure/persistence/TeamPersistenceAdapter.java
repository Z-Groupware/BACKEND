package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TeamPersistenceAdapter implements TeamRepository {

    /* 회사 안 부서 이름 유일성을 최종 차단하는 데이터베이스 제약 이름이다(V2.3.16). */
    private static final String TEAM_NAME_UNIQUE_CONSTRAINT = "UK_TEAM_COMPANY_NAME";

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
    public Team create(Long companyId, String name) {
        TeamJpaEntity saved;
        try {
            /* flush까지 실행해 유일성 제약 위반을 이 메서드 경계에서 확인한다. */
            saved = repository.saveAndFlush(TeamJpaEntity.create(companyId, name));
        } catch (DataIntegrityViolationException exception) {
            throw translateNameDuplicate(exception);
        }
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
        /* 서비스가 이미 존재를 확인했더라도, 그 확인과 이 갱신 사이에 동시 삭제가 끼어들면
         * 대상이 없어질 수 있다 — 조용히 넘기지 않고 명시적으로 실패시킨다. */
        TeamJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        entity.rename(name);
        try {
            /* 커밋까지 기다리지 않고 이 메서드 경계에서 유일성 제약 위반을 확인한다. */
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateNameDuplicate(exception);
        }
    }

    /* 이름 유일성 제약 위반만 공개 계약인 TEAM_NAME_DUPLICATED로 변환하고 다른 무결성 오류는 숨기지 않는다. */
    private RuntimeException translateNameDuplicate(DataIntegrityViolationException exception) {
        if (containsConstraintName(exception, TEAM_NAME_UNIQUE_CONSTRAINT)) {
            return new BusinessException(AuthErrorCode.TEAM_NAME_DUPLICATED, exception);
        }
        return exception;
    }

    /* 예외 원인 체인에 특정 데이터베이스 제약 이름이 포함돼 있는지 확인한다. */
    private boolean containsConstraintName(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains(constraintName.toUpperCase(Locale.ROOT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    @Transactional
    public void updateLeader(Long id, Long leaderMemberId) {
        TeamJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        entity.updateLeader(leaderMemberId);
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    public boolean existsByCompanyIdAndName(Long companyId, String name) {
        return repository.existsByCompanyIdAndName(companyId, name);
    }

    private Team toDomain(TeamJpaEntity entity) {
        return new Team(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getLeaderMemberId());
    }
}
