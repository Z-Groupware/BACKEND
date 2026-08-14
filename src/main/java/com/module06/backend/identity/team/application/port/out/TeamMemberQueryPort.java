package com.module06.backend.identity.team.application.port.out;

import java.util.List;

public interface TeamMemberQueryPort {

    List<TeamMemberSummary> findActiveMembersByCompany(Long companyId);

    boolean hasActiveMembers(Long teamId);

    /**
     * 이 역할을 달고 있는 재직자 수. 두 곳이 같은 값을 쓴다 — 역할 삭제(§6-12)를 막는 조건이고
     * (부서 삭제(§6-4)와 같은 원칙으로 배정을 자동으로 풀지 않는다), 화면에 "N명이 이 역할을 쓰고
     * 있습니다"로 그대로 나가는 숫자다. 같은 축으로 세야 "0명인데 삭제가 막힌다"가 생기지 않는다.
     *
     * <p>퇴사자는 세지 않는다. 화면에 보이지도 않는 사람 때문에 역할을 영원히 못 지우게 되고,
     * 끊긴 참조를 읽는 경로도 없다 — 역할 이름을 읽는 조회는 모두 재직자만 본다.
     */
    long countActiveMembersWithRole(Long roleId);

    /** {@code roleId} 는 NOT NULL 이다(V2.3.10 — 역할 없음도 실제 행 id 2). */
    record TeamMemberSummary(Long memberId, Long teamId, Long roleId, String name) {
    }
}
