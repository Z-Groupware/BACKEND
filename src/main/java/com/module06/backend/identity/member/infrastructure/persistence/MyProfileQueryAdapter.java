package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;
import com.module06.backend.identity.member.domain.model.Plan;

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
        SubTeamRefEntity subTeam = member.getSubTeam();
        JobPositionRefEntity position = member.getJobPosition();

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
                subTeam == null ? null : subTeam.getName(),
                position == null ? null : position.getId(),
                position == null ? null : position.getName(),

                member.getRole(),
                member.isAdmin(),
                company.getOnboardedAt() != null,

                member.getStatus(),
                member.getJoinedOn(),
                planOf(company.getId()));
    }

    /** 구독 행이 없거나 전부 해지면 FREE 다 — 신설 회사는 구독 행이 아직 없다. */
    private Plan planOf(Long companyId) {
        return subscriptionRepository.findFirstByCompanyIdAndStatusOrderByIdDesc(companyId, ACTIVE)
                .map(SubscriptionRefEntity::getPlan)
                .orElse(Plan.FREE);
    }
}
