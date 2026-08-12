package com.module06.backend.identity.member.application.usecase;

/**
 * 오너 대시보드 KPI 카드 중 identity(B) 소유분 — "전체 사원"·"휴직자". "전체 프로젝트"·"마감 D-7"
 * 은 project(C) 소유라 {@code GetOwnerDashboardSummaryUseCase} 가 따로 낸다(이슈 #352 — 각 도메인이
 * 자기 소유 숫자만 낸다). FE 가 두 응답을 화면에서 합친다.
 *
 * <p>연결된 클래스
 * <ul>
 *   <li>MemberDirectoryService : 구현체 (application.service)</li>
 *   <li>MemberController       : 호출자 (presentation)</li>
 * </ul>
 */
public interface GetMemberDashboardSummaryUseCase {

    MemberDashboardSummary getDashboardSummary(Long companyId);

    /**
     * @param totalMemberCount  지금 회사에 소속된 인원 — ACTIVE + VACATION + WAITING. 퇴사(RESIGNED)·
     *                          소프트삭제된 구성원은 빠진다.
     * @param onLeaveMemberCount 그중 실제로 휴직 중인 인원 — VACATION 만. 승인 대기(WAITING)는 넣지
     *                          않는다: 아직 휴직이 시작되지 않았고, WAITING 에는 오프보딩 대기도 섞여 있다.
     */
    record MemberDashboardSummary(long totalMemberCount, long onLeaveMemberCount) {
    }
}
