package com.module06.backend.action.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    project_attachment 테이블용 읽기 전용 Spring Data JPA 인터페이스(FR-AC-06 팀 액션 상세용).
    SpringDataProjectReferenceRepository와 동일한 참조 전용 패턴.

    연결된 클래스
    - ProjectAttachmentReferenceEntity : 다루는 엔티티
    - ActionReferenceRepositoryAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataProjectAttachmentReferenceRepository extends JpaRepository<ProjectAttachmentReferenceEntity, Long> {

    List<ProjectAttachmentReferenceEntity> findAllByProjectId(Long projectId);
}
