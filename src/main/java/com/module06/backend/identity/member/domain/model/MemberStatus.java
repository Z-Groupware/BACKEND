package com.module06.backend.identity.member.domain.model;

/**
 * member.status 와 값 이름이 정확히 같아야 한다 — @Enumerated(STRING) 으로 매핑한다(V2.2.12).
 *
 * <p>RESIGNED 는 오프보딩 최종 승인의 감사 흔적용 내부값이다. 퇴직자는 deleted_at 으로 걸러지므로
 * 조회 결과에 나타나지 않는다.
 */
public enum MemberStatus {
    ACTIVE, VACATION, WAITING, RESIGNED
}
