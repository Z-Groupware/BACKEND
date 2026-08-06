package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.MemberStatusPort;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 인수인계·휴직이 요청하는 멤버 생애주기를 수행한다 — {@link MemberStatusPort} 의 구현이다.
 *
 * <p>포트는 인수인계 도메인이 선언했지만 구현이 여기 있는 이유: 상태 enum·전이 규칙·감사를
 * 계정 도메인이 소유하기 때문이다(포트 javadoc). 인수인계는 {@code MemberStatus} 를 알지 못한 채
 * 의도만 던진다.
 *
 * <p>전이 검사는 이 클래스가 아니라 {@link MemberJpaEntity} 가 한다 — 호출 경로가 늘어나도
 * 규칙이 한 곳에만 있어야 한다. 여기서는 찾아서 위임하고, 오프보딩의 부수 효과만 더한다.
 */
@Repository
@RequiredArgsConstructor
@Transactional
public class MemberStatusAdapter implements MemberStatusPort {

    private final SpringDataMemberRepository memberRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    @Override
    public void toWaiting(Long memberId) {
        find(memberId).requestLeave();
    }

    @Override
    public void toVacation(Long memberId) {
        find(memberId).approveVacation();
    }

    @Override
    public void restoreActive(Long memberId) {
        find(memberId).restoreActive();
    }

    /**
     * 오프보딩은 상태만 바꾸는 것으로 끝나지 않는다. 계정을 닫아도 이미 발급된 갱신표가 남아 있으면
     * 퇴사자가 최대 14일간 스스로 액세스 토큰을 재발급할 수 있다 — 그래서 함께 폐기한다.
     *
     * <p>순서가 중요하다. 전이 검사를 먼저 통과시켜야, 잘못된 요청으로 남의 세션을 끊는 일이 없다.
     */
    @Override
    public void offboard(Long memberId) {
        find(memberId).offboard(LocalDateTime.now(clock));
        refreshTokenStore.revokeAllByMember(memberId);
    }

    private MemberJpaEntity find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }
}
