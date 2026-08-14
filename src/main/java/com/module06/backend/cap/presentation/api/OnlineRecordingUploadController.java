package com.module06.backend.cap.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.module06.backend.cap.application.usecase.IssueOnlineRecordingUploadUrlUseCase;
import com.module06.backend.cap.presentation.api.dto.request.OnlineRecordingUploadUrlRequest;
import com.module06.backend.cap.presentation.api.dto.response.OnlineRecordingUploadUrlResponse;
import com.module06.backend.global.response.ApiResponse;

/* MEET-18 모달에서 선택한 파일을 프론트가 S3로 직접 전송할 수 있게 한다. */
@Tag(name = "Online Meeting Recording", description = "비대면 회의 녹음 S3 직접 업로드 API")
@RestController
@RequestMapping("/api/meetings/online/recordings")
@RequiredArgsConstructor
public class OnlineRecordingUploadController {

    private final IssueOnlineRecordingUploadUrlUseCase issueOnlineRecordingUploadUrlUseCase;

    @Operation(
            summary = "비대면 회의 녹음 업로드 URL 발급",
            description = "파일 바이트는 백엔드를 거치지 않습니다. 반환된 URL에 프론트가 직접 PUT한 뒤 "
                    + "s3Key와 파일 메타데이터를 MEET-18 요청에 포함합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/upload-url")
    public ApiResponse<OnlineRecordingUploadUrlResponse> issueUploadUrl(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody OnlineRecordingUploadUrlRequest request
    ) {
        IssueOnlineRecordingUploadUrlUseCase.Result result = issueOnlineRecordingUploadUrlUseCase
                .issueOnlineRecordingUploadUrl(request.toCommand(companyId, memberId));
        return ApiResponse.success("비대면 회의 녹음 업로드 URL이 발급되었습니다.",
                OnlineRecordingUploadUrlResponse.from(result));
    }
}
