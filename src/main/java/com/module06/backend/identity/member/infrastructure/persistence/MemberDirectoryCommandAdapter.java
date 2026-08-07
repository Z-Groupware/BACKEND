package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.domain.model.Authority;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 구성원 관리 화면(§7)의 쓰기 창구 — {@link MemberDirectoryCommandPort} 구현. */
@Repository
@RequiredArgsConstructor
@Transactional
public class MemberDirectoryCommandAdapter implements MemberDirectoryCommandPort {

    /** 역할 "없음". 전 회사 공용 시스템 행이다(V2.3.9) — {@link OwnerAccountAdapter} 와 같은 상수다. */
    private static final long ROLE_NONE_ID = 2L;

    private final SpringDataMemberRepository memberRepository;
    private final EntityManager entityManager;

    @Override
    public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId) {
        MemberJpaEntity member = find(memberId);
        PositionRefEntity position = entityManager.getReference(PositionRefEntity.class, positionId);
        member.changeRoleAndPosition(authority, position);
    }

    @Override
    public void demoteToMember(Long memberId) {
        find(memberId).demoteToMember();
    }

    @Override
    public void updateAdmin(Long memberId, boolean isAdmin) {
        find(memberId).changeAdmin(isAdmin);
    }

    @Override
    public Long issue(Long companyId, Long teamId, Long positionId, String roleLabel,
                       String name, String email, String passwordHash, Authority authority) {
        CompanyJpaEntity company = entityManager.getReference(CompanyJpaEntity.class, companyId);
        TeamRefEntity team = entityManager.getReference(TeamRefEntity.class, teamId);
        PositionRefEntity position = entityManager.getReference(PositionRefEntity.class, positionId);
        if (roleLabel != null) {
            /*
             * 화면 폼에 없는 값이라(§5-1) roleLabel 로 role 을 찾는 조회 창구가 아직 없다.
             * 조용히 "없음"으로 발급하면 호출자가 지정한 역할이 사라진 채 성공 응답이 나가므로,
             * 여기서 명시적으로 막는다 — OrgQueryPort#findReassignCandidates 와 같은 원칙이다.
             */
            throw new UnsupportedOperationException(
                    "MemberDirectoryCommandPort#issue 는 roleLabel 로 role 을 찾는 창구가 아직 없다 "
                            + "(§5-1, FE 폼에 없는 값 — 현재 항상 null). this is not a silent fallback.");
        }
        RoleRefEntity role = entityManager.getReference(RoleRefEntity.class, ROLE_NONE_ID);

        MemberJpaEntity member = MemberJpaEntity.issue(company, team, role, position, name, email, passwordHash, authority);
        return memberRepository.save(member).getId();
    }

    private MemberJpaEntity find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }
}
