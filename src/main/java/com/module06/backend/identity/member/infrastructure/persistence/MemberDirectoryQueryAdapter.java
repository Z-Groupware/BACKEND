package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;
import com.module06.backend.identity.member.domain.model.Plan;

import lombok.RequiredArgsConstructor;

/** 구성원 관리 화면(§7)의 읽기 창구 — {@link MemberDirectoryQueryPort} 구현. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberDirectoryQueryAdapter implements MemberDirectoryQueryPort {

    private static final String ACTIVE_SUBSCRIPTION_STATUS = "ACTIVE";

    /** 이 두 상태면 더 이상 대기가 아니다 — handover.HandoverStatus 와 값이 같다. */
    private static final List<String> HANDOVER_CLOSED_STATUSES = List.of("FINALIZED", "REJECTED");

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataSubscriptionRepository subscriptionRepository;
    private final SpringDataHandoverPendingRefRepository handoverPendingRepository;

    @Override
    public List<MemberRow> findActiveByCompany(Long companyId) {
        List<MemberJpaEntity> members = memberRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        Map<Long, PendingHandoverType> pendingTypeByMemberId = pendingTypesOf(members);
        return members.stream()
                .map(member -> toRow(member, pendingTypeByMemberId))
                .toList();
    }

    @Override
    public Optional<MemberRow> findActiveById(Long companyId, Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .filter(member -> member.getCompany().getId().equals(companyId))
                .map(member -> toRow(member, pendingTypesOf(List.of(member))));
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

    /**
     * WAITING 인 구성원만 배치로 조회한다(§7-1 LEAVE_PENDING·OFFBOARDING_PENDING 구분).
     * 한 작성자가 대기 중인 handover 를 두 개 이상 가질 수 없다는 게 업무 규칙이지만, 데이터가
     * 어긋나 있으면 안전하게 가장 최근 것(id 최대)을 취한다.
     */
    private Map<Long, PendingHandoverType> pendingTypesOf(List<MemberJpaEntity> members) {
        List<Long> waitingMemberIds = members.stream()
                .filter(member -> member.getStatus() == MemberStatus.WAITING)
                .map(MemberJpaEntity::getId)
                .toList();
        if (waitingMemberIds.isEmpty()) {
            return Map.of();
        }
        return handoverPendingRepository.findByWriterMemberIdInAndStatusNotIn(waitingMemberIds, HANDOVER_CLOSED_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(
                        HandoverPendingRefEntity::getWriterMemberId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(HandoverPendingRefEntity::getId)),
                                latest -> PendingHandoverType.valueOf(latest.orElseThrow().getHandoverType()))));
    }

    private MemberRow toRow(MemberJpaEntity member, Map<Long, PendingHandoverType> pendingTypeByMemberId) {
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
                member.getJoinedOn(),
                pendingTypeByMemberId.get(member.getId()));
    }
}
