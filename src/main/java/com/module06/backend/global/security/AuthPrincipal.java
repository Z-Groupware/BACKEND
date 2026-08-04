package com.module06.backend.global.security;

/*
 * 인증된 요청자다. JWT 액세스 토큰의 클레임 5개를 그대로 담는다.
 *
 * 이 타입은 identity 도메인만의 것이 아니라 팀 전체가 쓰는 계약이다. 이미
 * MeetingRoomController 가 @AuthenticationPrincipal(expression = "companyId") 로 companyId 를
 * 꺼내 쓰고 있어서, 프로퍼티 이름을 바꾸면 그 코드가 조용히 깨진다.
 * record 접근자(companyId())만으로는 SpEL 이 못 읽으므로 getter 도 함께 노출한다.
 *
 * teamId 는 null 일 수 있다 — 오너는 온보딩을 끝내기 전까지 부서가 없다.
 */
public record AuthPrincipal(
        Long memberId,
        Long companyId,
        String role,
        boolean isAdmin,
        Long teamId
) {

    public Long getMemberId() {
        return memberId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getRole() {
        return role;
    }

    public Long getTeamId() {
        return teamId;
    }
}
