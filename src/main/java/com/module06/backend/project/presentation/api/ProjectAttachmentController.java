package com.module06.backend.project.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

import io.swagger.v3.oas.annotations.Parameter;
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

    회사·요청자를 토큰에서 꺼낸다(ProjectController 와 같은 이유). 헤더(X-Company-Id·X-Member-Id)로
    받던 것을 2026-08-08 에 바로잡았다 — 로그인만 한 사람이 남의 회사 번호를 적어 보내면 서비스의
    회사 검증이 그대로 통과했고, 삭제는 실제 업로더의 id 를 적는 것만으로 "업로더 본인만" 규칙이
    무력화됐다. 인증을 걸어도 막히지 않는 구멍이라 게이트가 아니라 신원의 출처를 고쳐야 한다.

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
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<IssuedUploadUrl> issueUploadUrl(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long projectId,
            @Valid @RequestBody IssueUploadUrlRequest request
    ) {
        IssuedUploadUrl issuedUploadUrl = issueAttachmentUploadUrlUseCase.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(companyId, projectId, request.fileName(), request.fileSize())
        );

        return ApiResponse.success("업로드 URL이 발급되었습니다.", issuedUploadUrl);
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<AttachmentResponse> confirm(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
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
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @PathVariable Long projectId,
            @PathVariable Long attachmentId
    ) {
        deleteAttachmentUseCase.delete(new DeleteAttachmentCommand(companyId, projectId, attachmentId, memberId));

        return ApiResponse.successWithoutData("첨부파일이 삭제되었습니다.");
    }
}
