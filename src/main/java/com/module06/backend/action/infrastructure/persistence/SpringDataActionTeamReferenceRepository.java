package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    ActionTeamReferenceEntity 조회 전용. ActionPersistenceAdapter가 "이 memberId가 팀장인
    팀들"을 먼저 찾은 뒤(파생 쿼리), 그 팀 id들로 action을 조회하는 2단계 방식에 쓴다
    — CI Gate 1(QUERY_002)이 신규 @Query(JPQL 조인)를 막아서 이렇게 우회(2026-08-06).
*/
public interface SpringDataActionTeamReferenceRepository extends JpaRepository<ActionTeamReferenceEntity, Long> {

    List<ActionTeamReferenceEntity> findAllByLeaderMemberId(Long leaderMemberId);
}
