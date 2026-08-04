package com.module06.backend.project.application.command;

/* comment.
    첨부파일 업로드 URL 발급 요청 명령. 프로젝트 id·파일명·파일 크기를 담는다.
    C는 shape 수준 검증만(파일명 비어있지 않음, 크기 > 0) 하고,
    실제 용량·확장자 정책 판단은 storage(F)가 URL 발급 시점에 한다.

    연결된 클래스
    - IssueUploadUrlRequest            : 이 명령으로 변환되는 요청 DTO (presentation)
    - IssueAttachmentUploadUrlUseCase  : 이 명령을 받는 기능 계약
    - ProjectAttachmentService  : 이 명령을 처리하는 구현체
    - ProjectAttachmentStoragePort     : URL 발급을 위임하는 경계
*/
public record IssueAttachmentUploadUrlCommand() {
}
