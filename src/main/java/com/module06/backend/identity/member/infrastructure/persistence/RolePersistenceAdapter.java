package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Role;
import com.module06.backend.identity.member.domain.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RolePersistenceAdapter implements RoleRepository {

    /* 부서 안 역할 이름 유일성을 최종 차단하는 데이터베이스 제약 이름이다(V2.3.23). */
    private static final String ROLE_NAME_UNIQUE_CONSTRAINT = "UK_ROLE_TEAM_NAME";

    private final SpringDataRoleWriteRepository repository;

    /**
     * flush 까지 실행해 유일성 제약 위반을 이 메서드 경계에서 확인한다
     * ({@code TeamPersistenceAdapter#create} 와 같은 이유). 커밋까지 미루면 온보딩 커밋(§4-1)처럼
     * 호출자의 try/catch 바깥에서 터져 그대로 500 이 된다.
     */
    @Override
    @Transactional
    public Long create(Long companyId, Long teamId, String name) {
        try {
            return repository.saveAndFlush(RoleWriteEntity.create(companyId, teamId, name)).getId();
        } catch (DataIntegrityViolationException exception) {
            throw translateNameDuplicate(exception);
        }
    }

    @Override
    public Optional<Role> findByIdAndCompanyIdAndTeamId(Long roleId, Long companyId, Long teamId) {
        return repository.findByIdAndCompanyIdAndTeamId(roleId, companyId, teamId).map(this::toDomain);
    }

    /**
     * 삭제 대상을 배타 잠금과 함께 읽는다 — 역할 배정과의 경합에서 끊긴 참조가 남지 않게 한다
     * ({@code SpringDataRoleWriteRepository#findSharedByIdAndCompanyIdAndTeamId} 주석 참조).
     *
     * <p>잠금은 트랜잭션이 있어야 한다. 호출자(TeamRoleService#delete)가 이미 트랜잭션 안이지만
     * 단독 호출에서도 안전하도록 여기에도 명시한다({@code TeamPersistenceAdapter#findByLeaderMemberId}
     * 와 같은 이유).
     */
    @Override
    @Transactional
    public Optional<Role> lockByIdAndCompanyIdAndTeamId(Long roleId, Long companyId, Long teamId) {
        return repository.findLockedByIdAndCompanyIdAndTeamId(roleId, companyId, teamId).map(this::toDomain);
    }

    @Override
    public boolean existsByTeamIdAndName(Long teamId, String name) {
        return repository.existsByTeamIdAndName(teamId, name);
    }

    /**
     * {@code TeamPersistenceAdapter#rename} 과 같은 이유로 {@code @Transactional} 이 붙는다 —
     * {@code findById} 가 주는 관리 상태(managed)가 flush 되는 순간까지 살아 있어야 dirty checking
     * 으로 이름 변경이 실제로 반영된다.
     *
     * <p>서비스가 이미 존재를 확인했더라도 그 확인과 이 갱신 사이에 동시 삭제가 끼어들 수 있어
     * 여기서 한 번 더 명시적으로 실패시킨다.
     */
    @Override
    @Transactional
    public void rename(Long roleId, String name) {
        RoleWriteEntity entity = repository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ROLE_NOT_FOUND));
        entity.rename(name);
        try {
            /* 커밋까지 기다리지 않고 이 메서드 경계에서 유일성 제약 위반을 확인한다. */
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateNameDuplicate(exception);
        }
    }

    @Override
    public void delete(Long roleId) {
        repository.deleteById(roleId);
    }

    /* 이름 유일성 위반만 공개 계약인 ROLE_NAME_DUPLICATED 로 바꾸고 다른 무결성 오류는 숨기지 않는다. */
    private RuntimeException translateNameDuplicate(DataIntegrityViolationException exception) {
        if (containsConstraintName(exception, ROLE_NAME_UNIQUE_CONSTRAINT)) {
            return new BusinessException(AuthErrorCode.ROLE_NAME_DUPLICATED, exception);
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

    private Role toDomain(RoleWriteEntity entity) {
        return new Role(entity.getId(), entity.getCompanyId(), entity.getTeamId(), entity.getName());
    }
}
