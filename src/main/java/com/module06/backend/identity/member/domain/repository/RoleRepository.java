package com.module06.backend.identity.member.domain.repository;

/**
 * "역할"(부서 안의 하위 구분, 구 {@code sub_team})의 쓰기 창구. 온보딩 커밋(§4-1)만 쓴다.
 *
 * <p>조회 메서드가 없다 — 호출자(온보딩)가 같은 요청 안에서 방금 만든 역할의 id를 이미 알고
 * 있어 되읽을 필요가 없다. 화면 표시용 읽기는 {@code RoleRefEntity}(member 패키지, 읽기 전용)가
 * 이미 맡고 있다.
 */
public interface RoleRepository {

    /** @return 생성된 역할 id */
    Long create(Long companyId, Long teamId, String name);
}
