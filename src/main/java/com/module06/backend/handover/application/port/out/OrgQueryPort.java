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

    /**
     * 오너·어드민의 "회사 전체" 조회 범위. 그 회사 구성원의 id 를 모아 준다 —
     * 호출자는 {@code findByWriterMemberIdIn} 으로 인수인계를 걸러낸다.
     *
     * <p>handover 테이블에 {@code company_id} 를 박지 않고 매번 물어보는 구조다(2026-08-06 결정).
     * 스냅샷으로 두면 조직 이동·개편 때 낡은 값이 남는데, "오너가 보는 회사 승인 큐" 는 현재 조직
     * 진실을 따라야 한다. 작성 시점 팀({@code team_id})은 스냅샷이 맞아 그대로 둔다.
     *
     * <p><b>퇴사자도 포함한다.</b> 오프보딩이 끝나면 작성자가 {@code RESIGNED} 가 되는데, 빼면
     * 방금 승인한 인수인계가 목록에서 사라져 감사 흔적을 볼 수 없다. 진행 중 여부는 호출자가
     * status 로 가른다.
     *
     * <p>id 목록을 그대로 넘기는 방식은 회사 규모가 수십 명인 현재 타깃에 맞춘 것이다. 대규모
     * 고객이 생기면 {@code handover.company_id} 컬럼 + 백필로 바꾸는 것이 탈출구다.
     */
    List<Long> findMemberIdsByCompany(Long companyId);

    record MemberSnapshot(Long memberId, String name, String position) {
    }

    record ReassignCandidate(Long memberId, String name, String position, int actionCount) {
    }

    record MemberSummary(Long memberId, String name, String position) {
    }
}
