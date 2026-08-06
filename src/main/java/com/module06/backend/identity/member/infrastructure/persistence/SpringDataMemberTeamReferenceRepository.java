package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 부서장 조회용. {@link TeamRefEntity} 가 {@code @Immutable} 이라 읽기만 가능하다 —
 * 부서 자체의 생성·수정은 기업 설정 API(다른 담당)의 몫이다.
 */
interface SpringDataMemberTeamReferenceRepository extends JpaRepository<TeamRefEntity, Long> {
}
