package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}
