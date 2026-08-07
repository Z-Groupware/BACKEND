package com.module06.backend.identity.member.application.port.out;

import com.module06.backend.identity.member.domain.model.Authority;

/** 구성원 관리 화면(§7)이 쓰는 창구. 상태 전이(휴직·오프보딩)는 다루지 않는다 — 그건 MemberStatusPort 몫이다. */
public interface MemberDirectoryCommandPort {

    /** §7-4. 역할·직급을 한 트랜잭션에 같이 바꾼다 — 따로 두면 중간 상태가 저장된다. */
    void updateRoleAndPosition(Long memberId, Authority authority, Long positionId);

    /** §7-4 팀장 교체 부수효과 — 기존 리더를 멤버로 내린다. 직급은 건드리지 않는다. */
    void demoteToMember(Long memberId);

    /** §7-7. isAdmin 만 바꾼다 — role 은 건드리지 않는다. */
    void updateAdmin(Long memberId, boolean isAdmin);

    /**
     * §5-1. {@code roleLabel} 이 null 이면 "역할 없음"으로 발급한다(오너 생성과 같은 규칙).
     * 이미 해시된 비밀번호를 받는다 — 평문은 이 경계를 넘지 않는다.
     *
     * @return 생성된 구성원 id
     */
    Long issue(Long companyId, Long teamId, Long positionId, String roleLabel,
               String name, String email, String passwordHash, Authority authority);
}
