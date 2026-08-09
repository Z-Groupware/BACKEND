package com.module06.backend.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    project_attachment 테이블용 Spring Data JPA 인터페이스. 상세조회 시 project_id
    기준으로 목록을 함께 읽는 게 주 경로다.
*/
public interface SpringDataProjectAttachmentRepository extends JpaRepository<ProjectAttachmentJpaEntity, Long> {

    List<ProjectAttachmentJpaEntity> findAllByProjectId(Long projectId);

    Optional<ProjectAttachmentJpaEntity> findByProjectIdAndFileUrl(Long projectId, String fileUrl);
}
