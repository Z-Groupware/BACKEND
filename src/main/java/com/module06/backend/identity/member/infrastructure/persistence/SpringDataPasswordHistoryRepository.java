package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 비밀번호 이력. 파생 메서드 하나뿐이다 — 신규 {@code @Query} 는 Gate 1(QUERY_002)이 막는다
 * ({@link SpringDataMemberRepository} 와 같은 규칙).
 */
interface SpringDataPasswordHistoryRepository extends JpaRepository<PasswordHistoryJpaEntity, Long> {

    /**
     * 이 구성원이 예전에 쓰던 해시 전부.
     *
     * <p>상한이 없다. 검증이 행 수만큼 BCrypt 를 돌리므로(1회당 수십 ms) 아주 자주 바꾸는 계정은
     * 응답이 느려질 수 있다 — 실제로 문제가 되면 최근 N개만 읽도록 여기를 자르면 되고, 그때
     * V2.3.23 마이그레이션의 주석도 함께 고친다.
     */
    List<PasswordHistoryJpaEntity> findByMemberId(Long memberId);
}
