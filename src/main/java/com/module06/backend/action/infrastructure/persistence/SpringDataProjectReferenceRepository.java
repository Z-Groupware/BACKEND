package com.module06.backend.action.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    ProjectReferenceEntity 배치조회 전용. ActionReassignAdapter가 인수인계 조회 결과를
    E에게 넘기기 전 projectTag 표시값을 채우는 데 쓴다(N+1 방지 위해 findAllById로 일괄조회).
*/
public interface SpringDataProjectReferenceRepository extends JpaRepository<ProjectReferenceEntity, Long> {
}
