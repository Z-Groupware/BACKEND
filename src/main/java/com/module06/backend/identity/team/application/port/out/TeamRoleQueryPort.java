package com.module06.backend.identity.team.application.port.out;

import java.util.List;

/**
 * team 도메인이 보는 역할(구 sub_team, V2.3.4)의 읽기 창구. 부서 목록에 그 부서의 역할을 함께
 * 실어 주기 위한 것이다 — 쓰기는 온보딩 커밋(§4-1)만 하고 그쪽은 member 도메인의
 * {@code RoleRepository} 를 쓴다.
 *
 * <p>부서 목록과 따로 떼어 별도 엔드포인트로 두지 않는다. 계정 발급 모달은 부서를 고를 때마다
 * 그 부서의 역할을 다시 그려야 해서, 부서마다 왕복하는 것보다 회사의 부서·역할을 한 번에 받는
 * 쪽이 화면당 요청이 적다(2026-08-14 프론트엔드 요청).
 */
public interface TeamRoleQueryPort {

    /**
     * 화면의 역할 select 에 그대로 올릴 수 있는 역할만 준다.
     *
     * <p>{@code teamId} 가 null 인 항목은 특정 부서에 매이지 않은 시스템 역할이다(V2.3.9) —
     * 호출자가 모든 부서에 공통으로 붙인다.
     */
    List<RoleSummary> findAssignableByCompany(Long companyId);

    record RoleSummary(Long roleId, Long teamId, String name) {
    }
}
