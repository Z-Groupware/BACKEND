package com.module06.backend.identity.member.application.dto;

/**
 * 회의 참석자 픽커가 쓰는 팀 로스터 한 줄(2026-08-13, 회의 도메인 요청). 이름과 id 뿐이다.
 *
 * <p>직급·권한·담당 액션 수를 넣지 않는다 — 체크박스 목록을 그리는 데 필요 없고, 팀원 현황
 * ({@code GET /api/team/members}, 액션 도메인)이 팀장 전용인 이유가 그 관리 정보에 있다.
 * 여기에 같은 필드를 실으면 팀장 전용으로 막아둔 값이 일반 사원 경로로 새어 나간다.
 */
public record TeamRosterMember(
        Long memberId,
        String name
) {
}
