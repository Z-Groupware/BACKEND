package com.module06.backend.identity.company.application.port.out;

/**
 * 기업 등록이 오너 계정을 만들 때 쓰는 출구. 구현은 member 도메인이 가진다.
 *
 * <p>포트를 소비자(company) 쪽에 두는 것은 이 저장소의 규약이다 — handover 가
 * {@code OrgQueryPort} 를 소유하고 member 가 구현하는 것과 같다. 이렇게 두면 member 가
 * company 를 모르는 상태로 남고, 필요한 모양을 부르는 쪽이 정한다.
 *
 * <p>회사 생성과 같은 트랜잭션 안에서 불린다. 따로 커밋하면 오너 없는 회사가 남고,
 * 그 회사는 아무도 로그인할 수 없어 되돌릴 경로가 없다.
 */
public interface OwnerAccountPort {

    /**
     * 오너 계정을 만들고 id 를 돌려준다.
     *
     * <p>{@code authority}·{@code isAdmin} 을 인자로 받지 않는다. OWNER·true 로 고정이며,
     * 인자로 열면 이 포트가 임의의 권한을 가진 계정을 만드는 통로가 된다.
     *
     * @param passwordHash 이미 해싱된 값. 평문을 넘기지 않는다 — 이 경계를 지나면 로그에 찍힐 수 있다.
     */
    Long createOwner(Long companyId, String name, String email, String passwordHash);
}
