package com.module06.backend.project.domain.repository;

/* comment.
    프로젝트 첨부파일 메타데이터 저장소 계약. domain이 선언하고 infrastructure가 구현한다.
    오브젝트 스토리지 접근은 이 계약의 책임이 아니다 — 그건 ProjectAttachmentStoragePort가 맡는다.

    연결된 클래스
    - ProjectAttachment                     : 다루는 도메인 모델
    - ProjectAttachmentPersistenceAdapter   : 구현체 (infrastructure.persistence)
    - ProjectAttachmentStoragePort          : 파일 실체를 다루는 별도 경계 (application.port)
*/
public interface ProjectAttachmentRepository {
}
