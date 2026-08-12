package com.module06.backend.action.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    PositionReferenceEntity 조회 전용. 팀원 현황(2026-08-11)의 "직급" 라벨 배치조회에 쓴다.
*/
public interface SpringDataPositionReferenceRepository extends JpaRepository<PositionReferenceEntity, Long> {
}
