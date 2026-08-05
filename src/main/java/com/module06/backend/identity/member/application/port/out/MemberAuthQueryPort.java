package com.module06.backend.identity.member.application.port.out;

import java.util.Optional;

import com.module06.backend.identity.member.application.dto.MemberCredentials;

public interface MemberAuthQueryPort {

    /**
     * 기업 안에서 이메일로 찾는다. 이메일은 전역이 아니라 회사 안에서만 유일하다
     * (UK_MEMBER_COMPANY_EMAIL) — companyId 없이 찾으면 다른 회사 사람이 걸린다.
     *
     * <p>퇴사자도 돌려준다. 거절 여부는 호출자가 {@code resigned} 를 보고 정한다.
     */
    Optional<MemberCredentials> findForLogin(Long companyId, String email);

    /**
     * 재발급용. 갱신표에는 {@code memberId} 와 {@code jti} 만 들어 있어서 새 액세스 토큰의
     * 클레임({@code role}·{@code isAdmin}·{@code teamId})을 채울 수 없다 — 그래서 다시 읽는다.
     *
     * <p>이건 비용이 아니라 이득이다. 권한이 바뀐 사람이 액세스 토큰 수명(30분)을 기다리지 않고
     * 다음 재발급에서 새 권한을 받는다.
     *
     * <p>{@link #findForLogin} 과 마찬가지로 퇴사자도 돌려준다. 걸러 버리면 "퇴사했다"와
     * "그런 구성원이 없다"가 같은 응답이 되어 호출자가 구분할 근거를 잃는다.
     */
    Optional<MemberCredentials> findById(Long memberId);
}
