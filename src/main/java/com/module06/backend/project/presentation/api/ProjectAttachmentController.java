package com.module06.backend.project.presentation.api;

/* comment.
    FR-PJ-08 — 프로젝트 첨부파일 API 진입점. 프로젝트 하위 리소스다.
    업로드는 발급 → FE 직접 업로드 → 확정 3단계로 나뉜다. BE는 바이너리를 받지 않는다.
    담당 엔드포인트
    - POST   /api/projects/{projectId}/attachments/upload-url   업로드 URL 발급
    - POST   /api/projects/{projectId}/attachments              업로드 확정(메타데이터 저장)
    - DELETE /api/projects/{projectId}/attachments/{attachmentId}  삭제
    첨부파일 목록은 별도 엔드포인트가 없다 — 프로젝트 상세 응답에 인라인으로 실린다.

    연결된 클래스
    - IssueAttachmentUploadUrlUseCase · ConfirmAttachmentUseCase · DeleteAttachmentUseCase : 호출 대상
    - IssueUploadUrlRequest · ConfirmAttachmentRequest                                     : 입력 DTO
    - AttachmentResponse                                                                   : 출력 DTO
*/
public class ProjectAttachmentController {
}
