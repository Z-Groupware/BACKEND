package com.module06.backend.identity.member.domain.model;

/**
 * member.status 와 값 이름이 정확히 같아야 한다 — @Enumerated(STRING) 으로 매핑한다(V2.2.12).
 *
 * <p>RESIGNED 는 오프보딩 최종 승인의 감사 흔적용 내부값이다. 퇴사자는 deleted_at 으로 걸러지므로
 * 조회 결과에 나타나지 않는다. 본인이 계정을 없애는 탈퇴 경로는 없다 — 회사가 내보내는 퇴사만 있다.
 *
 * <p>DELETED 는 관리자가 구성원 관리 화면에서 바로 지운 계정이다(§7, V2.3.21). RESIGNED 와 나누는
 * 이유는 절차의 차이다 — 그쪽은 인수인계 최종 승인을 거쳤고 이쪽은 거치지 않았다. 목록에서 빠지는
 * 기준은 둘 다 deleted_at 이고, 상태 값은 "왜 닫혔는지"만 남긴다.
 */
public enum MemberStatus {
    ACTIVE, VACATION, WAITING, RESIGNED, DELETED
}
