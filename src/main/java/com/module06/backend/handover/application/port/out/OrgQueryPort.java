package com.module06.backend.handover.application.port.out;

import java.util.List;

/**
 * B(조직) 모듈 소유 아웃 포트 — 인수인계 베이스(작성자/재분배 대상 스냅샷)용.
 * (인사이트 레이어가 findMembers(List) 배치 조회를 증분 추가한다.)
 */
public interface OrgQueryPort {

    Long findTeamLeaderId(Long teamId);

    MemberSnapshot findMember(Long memberId);

    List<ReassignCandidate> findReassignCandidates(Long teamId, Long excludeMemberId);

    record MemberSnapshot(Long memberId, String name, String position) {
    }

    record ReassignCandidate(Long memberId, String name, String position, int actionCount) {
    }
}
