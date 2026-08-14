package com.module06.backend.identity.member.application.port.out;

import java.util.List;

/**
 * 마이페이지 비밀번호 변경(PATCH /api/auth/me/password)이 쓰는 창구.
 *
 * <p>{@code MemberAuthQueryPort}(읽기)·{@code MemberDirectoryCommandPort}(관리자 화면의 쓰기)와
 * 나누는 이유는 다루는 값이 다르기 때문이다. 여기만 비밀번호 해시를 쓰고, 여기만 이력 테이블을 안다.
 *
 * <p><b>평문은 이 경계를 넘지 않는다.</b> 두 메서드 모두 이미 해시된 값만 주고받는다 —
 * {@code OwnerAccountPort}·{@code MemberDirectoryCommandPort} 가 이미 못박아 둔 규약과 같다.
 */
public interface MemberPasswordPort {

    /**
     * 이 구성원이 예전에 쓰던 비밀번호 해시 전부. 지금 쓰는 해시는 포함하지 않는다 —
     * 그건 {@code MemberAuthQueryPort.findById} 가 주는 값이고, 호출자가 따로 비교한다.
     *
     * <p>반환값으로 "같은 비밀번호인지"를 판정할 수는 없다. BCrypt 해시는 같은 평문이라도 매번
     * 다르므로, 호출자가 {@code PasswordEncoder.matches} 로 하나씩 대조해야 한다.
     */
    List<String> findUsedPasswordHashes(Long memberId);

    /**
     * 비밀번호를 바꾸고, <b>직전 해시를 이력으로 옮기고</b>, 변경 시각을 찍는다. 셋은 한 트랜잭션이다 —
     * 나누면 "바뀌었는데 이력에 없어서 되돌릴 수 있는" 창이 생긴다.
     *
     * <p>이력 적재를 호출자가 아니라 구현이 하는 이유: 직전 해시가 무엇인지는 저장소가 이미 알고
     * 있고, 호출자에게 넘기면 그 값을 서비스 계층까지 들고 다니게 된다.
     *
     * @param newPasswordHash 이미 해싱된 값
     */
    void changePassword(Long memberId, String newPasswordHash);
}
