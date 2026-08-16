package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;

import lombok.RequiredArgsConstructor;

/**
 * 회의 참석자 roster 조회 창구 — {@link MemberQueryPort} 의 구현이다(MEET-01).
 *
 * <p>회사 경계와 조회 목적에 맞는 삭제 여부를 여기서 걸러낸다. 존재하지 않거나 다른 회사 구성원 id 는
 * 조용히 결과에서 빠지고, 과거 회의 조회에서는 퇴사 구성원의 상태를 함께 반환한다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryAdapter implements MemberQueryPort {

    private final SpringDataMemberRepository memberRepository;

    @Override
    public List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberRepository.findByCompanyIdAndIdInAndDeletedAtIsNull(companyId, memberIds).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public List<MemberSnapshot> findMembersIncludingDeleted(Long companyId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberRepository.findByCompanyIdAndIdIn(companyId, memberIds).stream()
                .map(this::toSnapshot)
                .toList();
    }

    private MemberSnapshot toSnapshot(MemberJpaEntity member) {
        TeamRefEntity team = member.getTeam();
        PositionRefEntity position = member.getPosition();
        return new MemberSnapshot(
                member.getId(),
                member.getName(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                position == null ? null : position.getName(),
                member.getStatus() == MemberStatus.RESIGNED);
    }
}
