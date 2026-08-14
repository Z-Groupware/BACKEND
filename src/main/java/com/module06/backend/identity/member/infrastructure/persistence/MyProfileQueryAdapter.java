package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MyProfileQueryAdapter implements MyProfileQueryPort {

    private static final String ACTIVE = "ACTIVE";

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataSubscriptionRepository subscriptionRepository;

    @Override
    public Optional<MyProfile> findByMemberId(Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId).map(this::toProfile);
    }

    private MyProfile toProfile(MemberJpaEntity member) {
        CompanyJpaEntity company = member.getCompany();
        TeamRefEntity team = member.getTeam();
        RoleRefEntity role = member.getRole();
        PositionRefEntity position = member.getPosition();

        return new MyProfile(
                member.getId(),
                company.getId(),
                company.getName(),
                company.getCode(),

                member.getName(),
                member.getEmail(),
                member.getPhone(),

                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                role == null ? null : role.getName(),
                position == null ? null : position.getId(),
                position == null ? null : position.getName(),

                member.getAuthority(),
                member.isAdmin(),
                company.getOnboardedAt() != null,

                member.getStatus(),
                member.getJoinedOn(),
                planOf(company.getId()));
    }

    /**
     * 살아 있는 구독이 없으면 null 이다. FREE 로 둘러대지 않는다.
     *
     * <p>결제 없이는 이용할 수 없는 구조이므로, 구독 행이 없는 회사는 "무료 플랜 이용자"가 아니라
     * "이용 권한이 없는 상태"다. FREE 를 내리면 프론트가 정상 이용자로 취급해 결제 안내를 띄우지 못한다.
     */
    private String planOf(Long companyId) {
        return subscriptionRepository.findFirstByCompanyIdAndStatusOrderByIdDesc(companyId, ACTIVE)
                .map(SubscriptionRefEntity::getPlanCode)
                .orElse(null);
    }
}
