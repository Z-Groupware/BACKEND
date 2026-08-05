package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.CompletePartUploadUseCase;
import com.module06.backend.cap.application.usecase.IssuePartUploadUrlsUseCase;
import com.module06.backend.cap.presentation.api.dto.request.CompletePartRequest;
import com.module06.backend.cap.presentation.api.dto.request.PresignPartsRequest;
import com.module06.backend.cap.presentation.api.dto.response.PresignedPartsResponse;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Capture Upload", description = "회의 녹음 청크 업로드(presign/complete) API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/parts")
public class CaptureUploadController {

    private final IssuePartUploadUrlsUseCase issuePartUploadUrlsUseCase;
    private final CompletePartUploadUseCase completePartUploadUseCase;

    public CaptureUploadController(IssuePartUploadUrlsUseCase issuePartUploadUrlsUseCase,
                                   CompletePartUploadUseCase completePartUploadUseCase) {
        this.issuePartUploadUrlsUseCase = issuePartUploadUrlsUseCase;
        this.completePartUploadUseCase = completePartUploadUseCase;
    }

    // 청크 업로드용 presigned URL 배치 발급 (CAP-04)
    @Operation(
            summary = "청크 업로드 URL 배치 발급",
            description = "청크를 올릴 수 있는 presigned URL을 count개 발급합니다. 첫 호출자가 자동으로 녹음자가 됩니다."
    )
    @PostMapping("/presign")
    public ApiResponse<PresignedPartsResponse> presign(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @Parameter(description = "TEMP: 인증 배선 전까지 직접 입력하는 요청자 member id", example = "7")
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody PresignPartsRequest request) {
        // TEMP: B(auth) 배선 전까지 헤더 브리지. SecurityContext로 교체 예정.
        IssuePartUploadUrlsUseCase.Result result =
                issuePartUploadUrlsUseCase.issuePartUploadUrls(request.toCommand(meetingId, memberId));
        return ApiResponse.success("presigned URL 발급이 완료되었습니다.", PresignedPartsResponse.from(result));
    }

    // 청크 업로드 완료 통보 (CAP-07)
    @Operation(
            summary = "청크 업로드 완료 통보",
            description = "청크 하나가 S3에 업로드 완료됐음을 서버에 기록합니다. 이 호출 자체가 하트비트입니다."
    )
    @PostMapping("/{seq}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> complete(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @Parameter(description = "청크 순번(세그먼트 내)") @PathVariable int seq,
            @Parameter(description = "TEMP: 인증 배선 전까지 직접 입력하는 요청자 member id", example = "7")
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CompletePartRequest request) {
        // TEMP: B(auth) 배선 전까지 헤더 브리지. SecurityContext로 교체 예정.
        completePartUploadUseCase.completePartUpload(request.toCommand(meetingId, memberId, seq));
        return ApiResponse.accepted("청크 업로드가 기록되었습니다.", null);
    }
}
