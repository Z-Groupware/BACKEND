package com.module06.backend.project.infrastructure.persistence;

import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;

/* comment.
    domain의 ProjectAttachmentRepository 계약을 JPA로 구현하는 어댑터.
    메타데이터만 다룬다 — 실제 오브젝트 조작은 ProjectAttachmentStoragePort의 몫이다.
    이 둘을 헷갈리면 "DB는 지웠는데 파일은 남는" 고아 상태가 생긴다.

    연결된 클래스
    - ProjectAttachmentRepository            : 구현하는 도메인 계약
    - SpringDataProjectAttachmentRepository  : 실제 쿼리 위임 대상
    - ProjectAttachmentJpaEntity             : 변환 대상 엔티티
    - ProjectAttachment                      : 변환 결과 도메인 모델
*/
public class ProjectAttachmentPersistenceAdapter implements ProjectAttachmentRepository {
}
