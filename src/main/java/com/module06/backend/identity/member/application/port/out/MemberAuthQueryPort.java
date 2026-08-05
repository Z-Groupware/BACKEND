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
}
