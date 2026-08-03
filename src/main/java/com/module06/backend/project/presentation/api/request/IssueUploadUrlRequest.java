package com.module06.backend.project.presentation.api.request;

/* comment.
    첨부파일 업로드 URL 발급 요청 DTO. 파일명·파일 크기를 담는다.
    여기서는 shape 검증만 한다(파일명 비어있지 않음, 크기 > 0).
    최대 용량·허용 확장자 같은 정책은 storage(F)가 발급 시점에 판단한다.

    연결된 클래스
    - ProjectAttachmentController      : 이 DTO를 받는 진입점
    - IssueAttachmentUploadUrlCommand  : 이 DTO가 변환되는 application 명령
*/
public record IssueUploadUrlRequest() {
}
