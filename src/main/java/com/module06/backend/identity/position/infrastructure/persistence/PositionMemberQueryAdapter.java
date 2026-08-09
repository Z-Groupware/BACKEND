package com.module06.backend.identity.position.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.identity.position.application.port.out.PositionMemberQueryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PositionMemberQueryAdapter implements PositionMemberQueryPort {

    private final SpringDataPositionMemberRefRepository repository;

    @Override
    public List<PositionMemberSummary> findActiveMembersByCompany(Long companyId) {
        return repository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(e -> new PositionMemberSummary(e.getId(), e.getPositionId()))
                .toList();
    }

    @Override
    public boolean hasActiveMembers(Long positionId) {
        return repository.existsByPositionIdAndDeletedAtIsNull(positionId);
    }
}
