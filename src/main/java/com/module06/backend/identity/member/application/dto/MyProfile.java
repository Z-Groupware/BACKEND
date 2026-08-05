package com.module06.backend.identity.member.application.dto;

import java.time.LocalDate;

import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.model.Role;

/**
 * /me 가 내리는 내용. 비밀번호 해시는 담지 않는다 — 로그인용 조회는 MemberAuthQueryPort 로 따로 간다.
 *
 * <p>teamId·teamName·roleLabel·jobPositionId·positionName 은 nullable 이다. 온보딩 전 오너는 다섯 개가
 * 전부 null 이다.
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
        String roleLabel,
        Long jobPositionId,
        String positionName,

        Role role,
        boolean isAdmin,
        boolean isOnboarded,

        MemberStatus workStatus,
        LocalDate joinedOn,
        Plan plan
) {

    /** 저장하지 않고 역할에서 뽑는다. isAdmin 은 반영하지 않는다. */
    public String landingPath() {
        return role.landingPath();
    }
}
