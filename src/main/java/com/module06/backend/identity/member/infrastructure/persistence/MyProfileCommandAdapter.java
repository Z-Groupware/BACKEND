package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.port.out.MyProfileCommandPort;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MyProfileCommandAdapter implements MyProfileCommandPort {

    private final SpringDataMemberRepository memberRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void updateProfile(Long memberId, Long teamId, Long positionId, String phone) {
        MemberJpaEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
        TeamRefEntity team = teamId == null ? null : entityManager.getReference(TeamRefEntity.class, teamId);
        PositionRefEntity position = positionId == null
                ? null : entityManager.getReference(PositionRefEntity.class, positionId);
        member.updateProfile(team, position, phone);
    }
}
