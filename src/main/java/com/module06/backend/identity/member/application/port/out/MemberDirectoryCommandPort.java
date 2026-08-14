package com.module06.backend.identity.member.application.port.out;

import com.module06.backend.identity.member.domain.model.Authority;

/** 구성원 관리 화면(§7)이 쓰는 창구. 상태 전이(휴직·오프보딩)는 다루지 않는다 — 그건 MemberStatusPort 몫이다. */
public interface MemberDirectoryCommandPort {

    /**
     * §7-4. 권한·직급·역할 라벨을 한 트랜잭션에 같이 바꾼다 — 따로 두면 중간 상태가 저장된다.
     *
     * @param roleId null 이면 역할 라벨을 그대로 둔다. 권한·직급과 달리 이 값만 선택이다
     */
    void updateRoleAndPosition(Long memberId, Authority authority, Long positionId, Long roleId);

    /**
     * §7 사원 삭제. 물리 삭제하지 않는다 — 회의·인수인계가 이 행을 참조하고 있어 지우면 이력이
     * 끊긴다. 상태를 DELETED 로 바꾸고 {@code deleted_at} 을 찍어 목록·로그인에서 제외한다.
     * 오프보딩 최종 승인(RESIGNED)과는 다른 사건이라 상태 값을 나눠 쓴다.
     */
    void softDelete(Long memberId);

    /** §7-4 팀장 교체 부수효과 — 기존 리더를 멤버로 내린다. 직급은 건드리지 않는다. */
    void demoteToMember(Long memberId);

    /** §7-7. isAdmin 만 바꾼다 — role 은 건드리지 않는다. */
    void updateAdmin(Long memberId, boolean isAdmin);

    /**
     * §4-1 온보딩·§5-1 계정 발급 공용. 이미 해시된 비밀번호를 받는다 — 평문은 이 경계를 넘지 않는다.
     *
     * <p>역할을 이름이 아니라 id 로 받는다. 온보딩은 같은 요청 안에서 방금 만든 역할의 id 를 이미
     * 알고 있고, 계정 발급 화면은 {@code GET /api/teams} 로 받은 부서별 역할 목록에서 고른 id 를
     * 그대로 보낸다 — 양쪽 다 이름으로 되돌릴 필요가 없다(2026-08-14 id 기준으로 통일).
     *
     * @param roleId null 이면 "역할 없음"으로 발급한다(오너 생성과 같은 규칙).
     *               호출자가 회사·부서 스코프를 미리 검증한다
     * @return 생성된 구성원 id
     */
    Long issue(Long companyId, Long teamId, Long positionId, Long roleId,
               String name, String email, String passwordHash, Authority authority);
}
