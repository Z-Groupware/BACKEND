package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.Plan;

import lombok.RequiredArgsConstructor;

/** 구성원 관리 화면(§7)의 읽기 창구 — {@link MemberDirectoryQueryPort} 구현. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberDirectoryQueryAdapter implements MemberDirectoryQueryPort {

    private static final String ACTIVE_SUBSCRIPTION_STATUS = "ACTIVE";

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataSubscriptionRepository subscriptionRepository;

    @Override
    public List<MemberRow> findActiveByCompany(Long companyId) {
        return memberRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<MemberRow> findActiveById(Long companyId, Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .filter(member -> member.getCompany().getId().equals(companyId))
                .map(this::toRow);
    }

    @Override
    public boolean existsActiveEmail(Long companyId, String email) {
        return memberRepository.existsByCompanyIdAndEmailAndDeletedAtIsNull(companyId, email);
    }

    @Override
    public Optional<Plan> findActivePlan(Long companyId) {
        return subscriptionRepository.findFirstByCompanyIdAndStatusOrderByIdDesc(companyId, ACTIVE_SUBSCRIPTION_STATUS)
                .map(SubscriptionRefEntity::getPlan);
    }

    private MemberRow toRow(MemberJpaEntity member) {
        TeamRefEntity team = member.getTeam();
        PositionRefEntity position = member.getPosition();
        RoleRefEntity role = member.getRole();
        return new MemberRow(
                member.getId(),
                member.getName(),
                member.getEmail(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                position == null ? null : position.getId(),
                position == null ? null : position.getName(),
                role == null ? null : role.getName(),
                member.getAuthority(),
                member.isAdmin(),
                member.getStatus(),
                member.getJoinedOn());
    }
}
