package com.module06.backend.identity.member.domain.model;

/**
 * 화면의 "역할"(부서 안의 하위 구분 — 프론트엔드·백엔드·인사, 구 {@code sub_team}, V2.3.4).
 *
 * <p>인가에 쓰지 않는 표시용 라벨이다 — 인가 축은 {@link Authority} 다.
 *
 * <p>{@code companyId}·{@code teamId} 가 둘 다 null 이면 전 회사 공용 시스템 역할이다
 * (id 1 = 리더, id 2 = 없음 — V2.3.9 시드). 회사가 만드는 역할은 둘 다 채워진다.
 */
public record Role(
        Long id,
        Long companyId,
        Long teamId,
        String name
) {

    /** 시스템 역할 "리더" — 회사가 만들거나 지울 수 없다(V2.3.9). */
    public static final long LEADER_ID = 1L;

    /** 시스템 역할 "없음" — 역할을 비우는 유일한 값이라 회사가 지울 수 없다(V2.3.9·V2.3.10). */
    public static final long NONE_ID = 2L;

    public static boolean isSystemRole(Long roleId) {
        return roleId != null && (roleId == LEADER_ID || roleId == NONE_ID);
    }
}
