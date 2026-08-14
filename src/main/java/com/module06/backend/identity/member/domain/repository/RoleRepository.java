package com.module06.backend.identity.member.domain.repository;

import java.util.Optional;

import com.module06.backend.identity.member.domain.model.Role;

/**
 * "역할"(부서 안의 하위 구분, 구 {@code sub_team})의 쓰기 창구.
 *
 * <p>온보딩 커밋(§4-1)이 {@link #create} 로 최초 구성을 만들고, 그 뒤의 편집은 부서 체계 화면의
 * 역할 CRUD(§6-10~6-12, {@code TeamRoleService})가 맡는다.
 *
 * <p>목록 조회 메서드가 없다 — 화면에 뿌리는 역할 목록은 부서 목록 응답이 이미 함께 싣고 있고
 * (team 도메인의 {@code TeamRoleQueryPort}), 여기 단건 조회는 편집 대상의 소유권(회사·부서)을
 * 확인하기 위한 것이다.
 *
 * <p>시스템 역할(id 1 리더 · 2 없음, V2.3.9)은 {@code company_id}·{@code team_id} 가 NULL 이라
 * 아래 회사 스코프 조회에 절대 잡히지 않는다 — 편집 대상에서 자동으로 빠진다.
 */
public interface RoleRepository {

    /** @return 생성된 역할 id */
    Long create(Long companyId, Long teamId, String name);

    /**
     * 편집 대상 역할을 회사·부서 스코프까지 확인해 가져온다 — 셋 중 하나라도 어긋나면 비어 있다.
     * 부서까지 조건에 넣는 이유는 경로가 {@code /api/teams/{teamId}/roles/{roleId}} 라
     * 남의 부서 역할 id 를 끼워 넣는 길이 실제로 열려 있기 때문이다.
     */
    Optional<Role> findByIdAndCompanyIdAndTeamId(Long roleId, Long companyId, Long teamId);

    /** 같은 부서 안 이름 중복 검사. 다른 부서에 같은 이름이 있는 것은 정상이다(§6-10). */
    boolean existsByTeamIdAndName(Long teamId, String name);

    void rename(Long roleId, String name);

    void delete(Long roleId);
}
