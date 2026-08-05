package com.module06.backend.handover.application.port.out;

import java.util.List;

/**
 * B(조직) 모듈 소유 아웃 포트 — 인수인계 베이스(작성자/재분배 대상 스냅샷) + 인사이트 배치 조회용.
 */
public interface OrgQueryPort {

    Long findTeamLeaderId(Long teamId);

    MemberSnapshot findMember(Long memberId);

    /**
     * 목록 스코프 A안: 오너·어드민의 "회사 전체" 조회용. 회사 소속 멤버 id 전부를 돌려준다.
     * handover에 company_id를 두지 않고(마이그레이션 회피) 조직(B)이 소유한 현재 진실을 따른다.
     */
    List<Long> findMemberIdsByCompany(Long companyId);

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
