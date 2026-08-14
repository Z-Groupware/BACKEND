package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.port.out.MemberPasswordPort;

import lombok.RequiredArgsConstructor;

/** 마이페이지 비밀번호 변경의 쓰기 창구 — {@link MemberPasswordPort} 구현. */
@Repository
@RequiredArgsConstructor
@Transactional
public class MemberPasswordAdapter implements MemberPasswordPort {

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataPasswordHistoryRepository passwordHistoryRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<String> findUsedPasswordHashes(Long memberId, Long companyId) {
        return passwordHistoryRepository.findByMemberIdAndCompanyId(memberId, companyId).stream()
                .map(PasswordHistoryJpaEntity::getPasswordHash)
                .toList();
    }

    /**
     * 이력을 <b>먼저</b> 넣고 그다음에 바꾼다. 순서가 반대면 엔티티에서 직전 해시가 이미 사라진 뒤라
     * 무엇을 이력으로 넣어야 할지 알 수 없다.
     *
     * <p>시각은 {@link Clock} 에서 읽는다 — {@code LocalDateTime.now()} 를 직접 부르면 테스트가
     * 시간을 고정할 수 없다({@link MemberDirectoryCommandAdapter#softDelete} 와 같은 규칙).
     */
    @Override
    public void changePassword(Long memberId, Long companyId, String newPasswordHash) {
        MemberJpaEntity member = archivePreviousHash(memberId, companyId);
        member.changePassword(newPasswordHash, LocalDateTime.now(clock));
    }

    @Override
    public void resetPassword(Long memberId, Long companyId, String newPasswordHash) {
        MemberJpaEntity member = archivePreviousHash(memberId, companyId);
        member.resetPassword(newPasswordHash);
    }

    /** 두 경로가 공유하는 부분 — 직전 해시를 이력으로 옮긴다. 갈리는 것은 변경 시각뿐이다. */
    private MemberJpaEntity archivePreviousHash(Long memberId, Long companyId) {
        MemberJpaEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        passwordHistoryRepository.save(PasswordHistoryJpaEntity.of(
                companyId, memberId, member.getPasswordHash(), LocalDateTime.now(clock)));
        return member;
    }
}
