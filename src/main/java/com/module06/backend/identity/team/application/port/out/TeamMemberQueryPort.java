package com.module06.backend.identity.team.application.port.out;

import java.util.List;

public interface TeamMemberQueryPort {

    List<TeamMemberSummary> findActiveMembersByCompany(Long companyId);

    boolean hasActiveMembers(Long teamId);

    /**
     * 이 역할을 달고 있는 재직자가 있는지. 역할 삭제(§6-12)를 막는 조건이다 — 부서 삭제(§6-4)와
     * 같은 원칙으로, 배정을 자동으로 풀지 않고 오너에게 "먼저 옮기세요"를 강제한다.
     */
    boolean hasActiveMembersWithRole(Long roleId);

    record TeamMemberSummary(Long memberId, Long teamId, String name) {
    }
}
