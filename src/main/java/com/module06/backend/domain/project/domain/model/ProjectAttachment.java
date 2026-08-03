package com.module06.backend.domain.project.domain.model;

/* comment.
    프로젝트 첨부파일의 메타데이터(파일명·URL·크기·업로더 id). 바이너리는 보관하지 않는다.
    실제 파일은 storage(F 도메인)가 오브젝트 스토리지에 보관하고, 여기는 그 참조만 가진다.
    파일 크기·확장자 제한은 storage 정책이라 이 모델은 shape 수준만 다룬다.

    연결된 클래스
    - Project                       : 소속 프로젝트 (project_id)
    - ProjectAttachmentRepository   : 저장소 계약
    - ProjectAttachmentStoragePort  : storage 도메인 경계 (application.port, 미생성)
    - ProjectAttachmentJpaEntity    : 영속화 매핑 (infrastructure.persistence, 미생성)
*/
public class ProjectAttachment {
}
