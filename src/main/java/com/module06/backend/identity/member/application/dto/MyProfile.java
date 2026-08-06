package com.module06.backend.identity.member.application.dto;

import java.time.LocalDate;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Plan;

/**
 * /me 가 내리는 내용. 비밀번호 해시는 담지 않는다 — 로그인용 조회는 MemberAuthQueryPort 로 따로 간다.
 *
 * <p>teamId·teamName·roleName·positionId·positionName 은 nullable 이다. 온보딩 전 오너는 다섯 개가
 * 전부 null 이다.
 *
 * <p>{@code roleName}(화면 "역할")과 {@code authority}(화면 "권한")를 혼동하지 않는다 — 전자는
 * 프론트엔드·백엔드 같은 라벨이고, 후자가 인가 축이다.
 *
 * <p>{@code plan} 도 nullable 이다 — 살아 있는 구독이 없으면 null 이고, 그것이 "결제가 필요한 상태"를
 * 뜻한다. FREE 로 둘러대지 않는다.
 */
public record MyProfile(
        Long memberId,
        Long companyId,
        String companyName,
        String companyCode,

        String name,
        String email,
        String phone,

        Long teamId,
        String teamName,
        String roleName,
        Long positionId,
        String positionName,

        Authority authority,
        boolean isAdmin,
        boolean isOnboarded,

        MemberStatus workStatus,
        LocalDate joinedOn,
        Plan plan
) {

    /** 저장하지 않고 권한에서 뽑는다. isAdmin 은 반영하지 않는다. */
    public String landingPath() {
        return authority.landingPath();
    }
}
