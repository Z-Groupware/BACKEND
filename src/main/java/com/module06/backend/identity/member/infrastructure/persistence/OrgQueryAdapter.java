package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 인수인계가 이름·직급 스냅샷을 얻는 창구 — {@link OrgQueryPort} 의 구현이다.
 *
 * <p>스냅샷은 감사 기록으로 박힌다({@code writerNameSnap}·{@code finalApproverNameSnap}).
 * 그래서 못 찾았을 때 조용히 null 을 주지 않고 예외를 던진다 — 빈 이름이 기록으로 남으면
 * 나중에 누가 승인했는지 알 수 없게 된다.
 *
 * <p>퇴사자도 걸러내지 않는다. 오프보딩 최종 승인은 계정을 soft delete 하면서 승인 기록을 남기고,
 * 이미 끝난 인수인계의 이름도 계속 읽어야 한다. 걸러내면 그 순간 조회가 실패해 승인이 멈춘다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgQueryAdapter implements OrgQueryPort {

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataMemberTeamReferenceRepository teamRepository;

    @Override
    public Long findTeamLeaderId(Long teamId) {
        return teamRepository.findById(teamId)
                .map(TeamRefEntity::getLeaderMemberId)
                .orElse(null);
    }

    @Override
    public MemberSnapshot findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .map(this::toSnapshot)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 배치 조회는 없는 id 때문에 전체를 실패시키지 않는다 — 찾은 것만 돌려준다.
     * 인사이트 조립은 "물어볼 후보"를 모으는 용도라, 한 명이 빠져도 나머지는 쓸 수 있어야 한다.
     */
    @Override
    public List<MemberSummary> findMembers(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        return memberRepository.findAllById(memberIds).stream()
                .map(member -> new MemberSummary(member.getId(), member.getName(), positionOf(member)))
                .toList();
    }

    /**
     * 회사 소속 구성원 id. 오너·어드민이 "회사 전체" 인수인계를 볼 때 그 범위가 된다.
     *
     * <p>퇴사자를 빼지 않는다 — 오프보딩이 끝나면 작성자가 {@code RESIGNED} 가 되는데, 빼면
     * 방금 승인한 인수인계가 목록에서 사라져 감사 흔적을 못 본다. 진행 중 여부는 호출자가
     * status 로 가른다.
     */
    @Override
    public List<Long> findMemberIdsByCompany(Long companyId) {
        return memberRepository.findByCompanyId(companyId).stream()
                .map(SpringDataMemberRepository.MemberIdProjection::getId)
                .toList();
    }

    /**
     * 구현하지 않는다 — {@code actionCount} 가 액션 도메인의 데이터라 이 도메인에서 셀 수 없다.
     *
     * <p>계정 도메인이 액션 테이블을 직접 조회하면 도메인 경계가 무너지고, 0 으로 채워 돌려주면
     * "액션이 없는 후보"와 구분되지 않아 재분배 대상을 잘못 고르게 된다. 그래서 조용히 넘기지 않는다.
     *
     * <p>호출자가 아직 없어 지금 막히는 기능은 없다. 재분배 화면을 만들 때, 후보 목록(계정)과
     * 액션 수(액션)를 각각 받아 인수인계가 조립하도록 포트를 나누는 편이 맞다 —
     * 그 결정은 인수인계·액션 담당의 것이다.
     */
    @Override
    public List<ReassignCandidate> findReassignCandidates(Long teamId, Long excludeMemberId) {
        throw new UnsupportedOperationException(
                "OrgQueryPort#findReassignCandidates 는 actionCount 가 액션 도메인 데이터라 "
                        + "계정 도메인에서 채울 수 없다. 포트를 나누거나 액션 도메인이 채워야 한다 "
                        + "(현재 호출자 없음). this is not a silent fallback.");
    }

    private MemberSnapshot toSnapshot(MemberJpaEntity member) {
        return new MemberSnapshot(member.getId(), member.getName(), positionOf(member));
    }

    /** 온보딩 전 오너는 직급이 없다 — null 이 정상 경로다. */
    private String positionOf(MemberJpaEntity member) {
        PositionRefEntity position = member.getPosition();
        return position == null ? null : position.getName();
    }
}
