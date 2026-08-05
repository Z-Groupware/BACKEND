package com.module06.backend.handover.application.port.out;

import java.util.List;

/**
 * B(조직) 모듈 소유 아웃 포트 — 인수인계 베이스(작성자/재분배 대상 스냅샷) + 인사이트 배치 조회용.
 */
public interface OrgQueryPort {

    Long findTeamLeaderId(Long teamId);

    MemberSnapshot findMember(Long memberId);

    List<ReassignCandidate> findReassignCandidates(Long teamId, Long excludeMemberId);

    // --- 인사이트 레이어 증분 계약 ---

    /** 인사이트 조립용: "누구에게 물어볼까" 후보 멤버 배치 조회. */
    List<MemberSummary> findMembers(List<Long> memberIds);

    record MemberSnapshot(Long memberId, String name, String position) {
    }

    record ReassignCandidate(Long memberId, String name, String position, int actionCount) {
    }

    record MemberSummary(Long memberId, String name, String position) {
    }
}
