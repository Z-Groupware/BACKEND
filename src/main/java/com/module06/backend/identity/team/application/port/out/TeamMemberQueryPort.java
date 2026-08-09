package com.module06.backend.identity.team.application.port.out;

import java.util.List;

public interface TeamMemberQueryPort {

    List<TeamMemberSummary> findActiveMembersByCompany(Long companyId);

    boolean hasActiveMembers(Long teamId);

    record TeamMemberSummary(Long memberId, Long teamId, String name) {
    }
}
