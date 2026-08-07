package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.application.port.out.MemberQueryPort;

import lombok.RequiredArgsConstructor;

/**
 * 회의 참석자 roster 조회 창구 — {@link MemberQueryPort} 의 구현이다(MEET-01).
 *
 * <p>회사 경계와 삭제 여부를 여기서 걸러낸다. 존재하지 않거나 다른 회사·퇴사한 구성원 id 는
 * 조용히 결과에서 빠진다 — roster 조립은 memberId 기준 재매핑이라 없는 항목이 있어도 문제가 없다.
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

    private MemberSnapshot toSnapshot(MemberJpaEntity member) {
        TeamRefEntity team = member.getTeam();
        return new MemberSnapshot(
                member.getId(),
                member.getName(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName());
    }
}
