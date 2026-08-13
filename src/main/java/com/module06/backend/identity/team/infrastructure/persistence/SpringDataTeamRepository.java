package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface SpringDataTeamRepository extends JpaRepository<TeamJpaEntity, Long> {

    List<TeamJpaEntity> findByCompanyId(Long companyId);

    Optional<TeamJpaEntity> findByIdAndCompanyId(Long id, Long companyId);

    /** MEET-17 대시보드 카드용 배치 조회 — 회사 경계를 쿼리에서 자른다(TeamQueryAdapter). */
    List<TeamJpaEntity> findByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    /**
     * 오프보딩(MemberStatusAdapter#offboard) 전용 — SELECT ... FOR UPDATE 로 이 행을 잠근다.
     * 조회와 이어지는 팀장 자리 비우기 사이에 다른 트랜잭션(후임 승급)이 끼어들면, 잠금 없이는
     * 방금 세운 후임을 이 UPDATE 가 도로 지워버릴 수 있다 — 승급 쪽의 UPDATE 가 이 트랜잭션이
     * 끝날 때까지 기다리게 해서 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TeamJpaEntity> findByLeaderMemberId(Long leaderMemberId);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
