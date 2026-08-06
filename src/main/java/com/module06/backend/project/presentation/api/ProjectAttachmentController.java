package com.module06.backend.project.presentation.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.project.application.command.ConfirmAttachmentCommand;
import com.module06.backend.project.application.command.DeleteAttachmentCommand;
import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;
import com.module06.backend.project.application.usecase.ConfirmAttachmentUseCase;
import com.module06.backend.project.application.usecase.DeleteAttachmentUseCase;
import com.module06.backend.project.application.usecase.IssueAttachmentUploadUrlUseCase;
import com.module06.backend.project.presentation.api.request.ConfirmAttachmentRequest;
import com.module06.backend.project.presentation.api.request.IssueUploadUrlRequest;
import com.module06.backend.project.presentation.api.response.AttachmentResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* comment.
    FR-PJ-08 — 프로젝트 첨부파일 API 진입점. 프로젝트 하위 리소스다.
    업로드는 발급 → FE 직접 업로드 → 확정 3단계로 나뉜다. BE는 바이너리를 받지 않는다.
    담당 엔드포인트 (경로는 아티팩트 "REST API 최종 목록" 기준 — 스캐폴딩 초안 주석과 confirm 경로가
    달랐던 것을 바로잡음, 08/06)
    - POST   /api/projects/{projectId}/attachments/upload-url   업로드 URL 발급
    - POST   /api/projects/{projectId}/attachments/confirm      업로드 확정(메타데이터 저장)
    - DELETE /api/projects/{projectId}/attachments/{attachmentId}  삭제
    첨부파일 목록 조회는 이 컨트롤러에 없음 — 별도 엔드포인트로 뺄지, 프로젝트 상세 응답에 인라인으로
    유지할지 아직 미정(08/06 TBD, 사용자 확인 필요).

    연결된 클래스
    - IssueAttachmentUploadUrlUseCase · ConfirmAttachmentUseCase · DeleteAttachmentUseCase : 호출 대상
    - IssueUploadUrlRequest · ConfirmAttachmentRequest                                     : 입력 DTO
    - AttachmentResponse                                                                   : 출력 DTO
*/
@RestController
@RequestMapping("/api/projects/{projectId}/attachments")
@RequiredArgsConstructor
public class ProjectAttachmentController {

    private final IssueAttachmentUploadUrlUseCase issueAttachmentUploadUrlUseCase;
    private final ConfirmAttachmentUseCase confirmAttachmentUseCase;
    private final DeleteAttachmentUseCase deleteAttachmentUseCase;

    @PostMapping("/upload-url")
    public ApiResponse<IssuedUploadUrl> issueUploadUrl(
            @PathVariable Long projectId,
            @Valid @RequestBody IssueUploadUrlRequest request
    ) {
        IssuedUploadUrl issuedUploadUrl = issueAttachmentUploadUrlUseCase.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(request.fileName(), request.fileSize())
        );

        return ApiResponse.success("업로드 URL이 발급되었습니다.", issuedUploadUrl);
    }

    @PostMapping("/confirm")
    public ApiResponse<AttachmentResponse> confirm(
            @RequestHeader("X-Company-Id") Long companyId,
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long projectId,
            @Valid @RequestBody ConfirmAttachmentRequest request
    ) {
        var attachment = confirmAttachmentUseCase.confirm(new ConfirmAttachmentCommand(
                projectId,
                companyId,
                request.fileName(),
                request.fileUrl(),
                request.fileSize(),
                memberId
        ));

        return ApiResponse.created("첨부파일이 등록되었습니다.", AttachmentResponse.from(attachment));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long projectId,
            @PathVariable Long attachmentId
    ) {
        deleteAttachmentUseCase.delete(new DeleteAttachmentCommand(attachmentId, memberId));

        return ApiResponse.successWithoutData("첨부파일이 삭제되었습니다.");
    }
}
