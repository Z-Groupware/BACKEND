package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    project(C, 같은 도메인이지만 다른 애그리거트) 소유 project_attachment 테이블을 읽기 전용으로
    조인하기 위한 참조 엔티티. ProjectReferenceEntity와 동일 원칙(애그리거트 경계를 넘는 직접
    참조 회피, 참조 전용, 쓰기 금지).
    팀 액션 상세(FR-AC-06)가 소속 프로젝트의 첨부파일을 인라인으로 내려줘야 해서 추가했다
    (2026-08-07).

    연결된 클래스
    - SpringDataProjectAttachmentReferenceRepository : findAllByProjectId로 조회
    - ActionReferenceRepositoryAdapter               : 도메인 계약(AttachmentReference)으로 변환
*/
@Entity
@Table(name = "project_attachment")
@Immutable
@Getter
@NoArgsConstructor
public class ProjectAttachmentReferenceEntity {

    // project_attachment 쓰기 엔티티(ProjectAttachmentJpaEntity)와 동일한 IDENTITY 전략 —
    // ProjectReferenceEntity에서 이미 겪은 것과 같은 이유(같은 테이블에 매핑된 두 엔티티의
    // 식별자 생성 메타데이터 혼동 방지).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_size")
    private long fileSize;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
