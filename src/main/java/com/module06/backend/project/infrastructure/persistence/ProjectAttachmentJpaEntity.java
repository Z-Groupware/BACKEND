package com.module06.backend.project.infrastructure.persistence;

/* comment.
    project_attachment 테이블 JPA 매핑. 도메인 모델 ProjectAttachment와 1:1로 변환된다.
    매핑 대상 컬럼: id·project_id·file_name·file_url·file_size·uploaded_by·created_at·updated_at.
    파일 바이너리는 담지 않는다 — file_url은 storage(F)가 관리하는 오브젝트 참조다.
    uploaded_by는 member 엔티티를 물지 않고 id 값으로만 둔다(0절 1항).

    연결된 클래스
    - ProjectAttachment                     : 변환 대상 도메인 모델
    - SpringDataProjectAttachmentRepository  : 이 엔티티를 다루는 Spring Data 인터페이스
    - ProjectAttachmentPersistenceAdapter    : 도메인 ↔ 엔티티 변환 담당
    - MemberReferenceEntity                  : 업로더 이름 조인 시 함께 읽는 참조 엔티티
*/
public class ProjectAttachmentJpaEntity {
}
