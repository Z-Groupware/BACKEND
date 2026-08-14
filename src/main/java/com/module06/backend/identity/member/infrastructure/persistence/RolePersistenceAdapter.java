package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

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

    private final SpringDataRoleWriteRepository repository;

    @Override
    @Transactional
    public Long create(Long companyId, Long teamId, String name) {
        return repository.saveAndFlush(RoleWriteEntity.create(companyId, teamId, name)).getId();
    }

    @Override
    public Optional<Role> findByIdAndCompanyIdAndTeamId(Long roleId, Long companyId, Long teamId) {
        return repository.findByIdAndCompanyIdAndTeamId(roleId, companyId, teamId).map(this::toDomain);
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
     * 여기서 한 번 더 명시적으로 실패시킨다. 번역할 제약 위반은 없다 — {@code role} 에는 이름
     * UNIQUE 가 없어(§6-10) 중복은 서비스의 사전 검사로만 막는다.
     */
    @Override
    @Transactional
    public void rename(Long roleId, String name) {
        RoleWriteEntity entity = repository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ROLE_NOT_FOUND));
        entity.rename(name);
        repository.flush();
    }

    @Override
    public void delete(Long roleId) {
        repository.deleteById(roleId);
    }

    private Role toDomain(RoleWriteEntity entity) {
        return new Role(entity.getId(), entity.getCompanyId(), entity.getTeamId(), entity.getName());
    }
}
