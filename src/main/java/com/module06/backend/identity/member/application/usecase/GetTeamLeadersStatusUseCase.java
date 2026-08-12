package com.module06.backend.identity.member.application.usecase;

import java.util.List;

import com.module06.backend.identity.member.application.dto.TeamLeaderStatus;

/**
 * 오너 대시보드 "팀장 현황" 테이블 — 회사의 팀마다 팀장 1행. 한 행이 전부 구성원 데이터라
 * 이 도메인이 낸다.
 *
 * <p>연결된 클래스
 * <ul>
 *   <li>MemberDirectoryService : 구현체 (application.service)</li>
 *   <li>MemberController       : 호출자 (presentation)</li>
 * </ul>
 */
public interface GetTeamLeadersStatusUseCase {

    /** 팀 id 오름차순. 리더가 공석인 팀은 행 자체가 빠진다. */
    List<TeamLeaderStatus> getTeamLeadersStatus(Long companyId);
}
