package com.module06.backend.project.infrastructure.persistence;

/* comment.
    project_attachment 테이블용 Spring Data JPA 인터페이스. 구현 시 JpaRepository를 상속한다.
    프로젝트 상세 조회에서 첨부파일 목록을 함께 읽으므로 project_id 기준 조회가 주 경로다.

    연결된 클래스
    - ProjectAttachmentJpaEntity          : 다루는 엔티티
    - ProjectAttachmentPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataProjectAttachmentRepository {
}
