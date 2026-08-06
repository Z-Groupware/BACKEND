package com.module06.backend.project.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/* comment.
    업로드 완료 확정 요청 DTO. 파일명·저장된 URL·파일 크기를 담는다.
    FE가 발급받은 URL로 업로드를 마친 직후 호출하며, 이 요청으로 메타데이터가 저장된다.

    연결된 클래스
    - ProjectAttachmentController : 이 DTO를 받는 진입점
    - ConfirmAttachmentCommand    : 이 DTO가 변환되는 application 명령
*/
public record ConfirmAttachmentRequest(
        @NotBlank String fileName,
        @NotBlank String fileUrl,
        @PositiveOrZero long fileSize
) {
}
