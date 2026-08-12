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

    /* 한 사람이 두 부서의 팀장이 되는 것을 최종 차단하는 데이터베이스 제약 이름이다(V2.2.6). */
    private static final String TEAM_LEADER_MEMBER_UNIQUE_CONSTRAINT = "UK_TEAM_LEADER_MEMBER";

    private final SpringDataTeamRepository repository;

    @Override
    public List<Team> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Team> findByIdAndCompanyId(Long id, Long companyId) {
        return repository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    /**
     * PESSIMISTIC_WRITE 락은 트랜잭션이 있어야 한다(SpringDataTeamRepository 참고) — 호출자가
     * 이미 트랜잭션 안이어도 이 메서드 자체에 명시해 단독 호출에서도 안전하게 한다.
     */
    @Override
    @Transactional
    public Optional<Team> findByLeaderMemberId(Long leaderMemberId) {
        return repository.findByLeaderMemberId(leaderMemberId).map(this::toDomain);
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

    /**
     * {@code rename} 과 같은 이유로 {@code @Transactional} 이 붙고, 같은 이유로 여기서 flush 한다.
     *
     * <p>flush 가 없으면 이 UPDATE 가 커밋 시점에야 나간다. 온보딩 커밋(§4-1)에서 이 메서드는
     * 마지막 초대까지 처리한 뒤 호출되는데, 그 자리는 모든 try/catch 바깥이라 제약 위반이
     * 나도 아무도 잡지 못하고 그대로 500(Z-003)이 된다. 메서드 경계에서 flush 해
     * {@code UK_TEAM_LEADER_MEMBER}(V2.2.6 — 한 사람이 두 부서의 팀장이 될 수 없다) 위반을
     * 공개 계약인 에러 코드로 바꾼다.
     */
    @Override
    @Transactional
    public void updateLeader(Long id, Long leaderMemberId) {
        TeamJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        entity.updateLeader(leaderMemberId);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateLeaderDuplicate(exception);
        }
    }

    /* 팀장 참조 유일성 위반만 §7 과 같은 AU-029 로 변환하고 다른 무결성 오류는 숨기지 않는다. */
    private RuntimeException translateLeaderDuplicate(DataIntegrityViolationException exception) {
        if (containsConstraintName(exception, TEAM_LEADER_MEMBER_UNIQUE_CONSTRAINT)) {
            return new BusinessException(AuthErrorCode.MEMBER_TEAM_LEADER_ALREADY_EXISTS, exception);
        }
        return exception;
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
