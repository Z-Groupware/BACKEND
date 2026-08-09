package com.module06.backend.identity.position.application.port.out;

import java.util.List;

public interface PositionMemberQueryPort {

    List<PositionMemberSummary> findActiveMembersByCompany(Long companyId);

    boolean hasActiveMembers(Long positionId);

    record PositionMemberSummary(Long memberId, Long positionId) {
    }
}
