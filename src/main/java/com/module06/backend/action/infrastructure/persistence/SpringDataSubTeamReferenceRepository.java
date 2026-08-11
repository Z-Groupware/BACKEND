package com.module06.backend.action.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    SubTeamReferenceEntity 조회 전용. 개인 액션 상세의 담당자 "역할" 라벨 배치 조회에 쓴다.
*/
public interface SpringDataSubTeamReferenceRepository extends JpaRepository<SubTeamReferenceEntity, Long> {
}
