package com.module06.backend.identity.member.application.dto;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * 로그인 검증에 필요한 최소한. 이름·전화번호·직급은 담지 않는다 — 화면에 뿌릴 값은 {@link MyProfile} 이다.
 *
 * <p>{@code authority}·{@code isAdmin}·{@code teamId} 는 토큰 클레임이 될 값이다.
 *
 * <p>{@code resigned} 는 퇴사 여부다. 조회에서 제외하지 않고 표시해서 넘기는 이유는, 로그인 쪽이
 * "비밀번호가 틀림"과 "퇴사한 계정"을 구분해 답해야 하기 때문이다. 걸러 버리면 둘이 같은 응답이 된다.
 */
public record MemberCredentials(
        Long memberId,
        Long companyId,
        String passwordHash,
        Authority authority,
        boolean isAdmin,
        Long teamId,
        boolean resigned
) {
}
