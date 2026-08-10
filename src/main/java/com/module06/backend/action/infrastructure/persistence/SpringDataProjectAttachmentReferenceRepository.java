package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

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

    // 다운로드 URL 발급(2026-08-10)용 단건 조회 — projectId까지 조건에 넣어 다른 프로젝트
    // 소속 첨부파일 id를 넣어도 DB 레벨에서부터 안 걸리게 한다.
    Optional<ProjectAttachmentReferenceEntity> findByIdAndProjectId(Long id, Long projectId);
}
