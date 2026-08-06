package com.module06.backend.identity.position.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.domain.model.Position;

public interface PositionRepository {

    List<Position> findByCompanyId(Long companyId);

    Optional<Position> findByIdAndCompanyId(Long id, Long companyId);

    Position create(Long companyId, String name, Authority authority, String description);

    void update(Long id, String name, Authority authority, String description);

    void delete(Long id);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
