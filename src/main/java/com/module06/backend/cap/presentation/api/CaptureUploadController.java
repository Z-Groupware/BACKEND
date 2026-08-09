package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.CompletePartUploadUseCase;
import com.module06.backend.cap.application.usecase.GetPartUploadStatusUseCase;
import com.module06.backend.cap.application.usecase.IssuePartUploadUrlsUseCase;
import com.module06.backend.cap.presentation.api.dto.request.CompletePartRequest;
import com.module06.backend.cap.presentation.api.dto.request.PresignPartsRequest;
import com.module06.backend.cap.presentation.api.dto.response.PartUploadStatusResponse;
import com.module06.backend.cap.presentation.api.dto.response.PresignedPartsResponse;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Capture Upload", description = "회의 녹음 청크 업로드(presign/complete) API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/parts")
public class CaptureUploadController {

    private final IssuePartUploadUrlsUseCase issuePartUploadUrlsUseCase;
    private final CompletePartUploadUseCase completePartUploadUseCase;
    private final GetPartUploadStatusUseCase getPartUploadStatusUseCase;

    public CaptureUploadController(IssuePartUploadUrlsUseCase issuePartUploadUrlsUseCase,
                                   CompletePartUploadUseCase completePartUploadUseCase,
                                   GetPartUploadStatusUseCase getPartUploadStatusUseCase) {
        this.issuePartUploadUrlsUseCase = issuePartUploadUrlsUseCase;
        this.completePartUploadUseCase = completePartUploadUseCase;
        this.getPartUploadStatusUseCase = getPartUploadStatusUseCase;
    }

    // 청크 업로드용 presigned URL 배치 발급 (CAP-04)
    @Operation(
            summary = "청크 업로드 URL 배치 발급",
            description = "청크를 올릴 수 있는 presigned URL을 count개 발급합니다. 첫 호출자가 자동으로 녹음자가 됩니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/presign")
    public ApiResponse<PresignedPartsResponse> presign(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody PresignPartsRequest request) {
        // 요청자는 헤더가 아니라 JWT principal에서 꺼낸다 — 남의 id로 녹음자 배정·업로드하는 것을 원천 차단.
        // (엔드포인트는 SecurityConfig에서 authenticated()로 보호됨 — 토큰 없으면 401)
        IssuePartUploadUrlsUseCase.Result result =
                issuePartUploadUrlsUseCase.issuePartUploadUrls(request.toCommand(meetingId, memberId));
        return ApiResponse.success("presigned URL 발급이 완료되었습니다.", PresignedPartsResponse.from(result));
    }

    // 청크 업로드 완료 통보 (CAP-07)
    @Operation(
            summary = "청크 업로드 완료 통보",
            description = "청크 하나가 S3에 업로드 완료됐음을 서버에 기록합니다. 이 호출 자체가 하트비트입니다."
    )
    // presign과 동일 — 이중 방어. "현재 녹음자만"은 서비스의 recordUpload가 검증한다.
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/{seq}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> complete(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @Parameter(description = "청크 순번(세그먼트 내)") @PathVariable int seq,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody CompletePartRequest request) {
        // 요청자는 JWT principal에서 꺼낸다(위 presign과 동일 원칙).
        completePartUploadUseCase.completePartUpload(request.toCommand(meetingId, memberId, seq));
        return ApiResponse.accepted("청크 업로드가 기록되었습니다.", null);
    }

    // 청크 업로드 상태 조회 (CAP-08)
    @Operation(
            summary = "청크 업로드 상태 조회",
            description = "현재 녹음자가 어디까지 올라갔는지(lastSeq·missingSeqs·resumeFromSeq 등)를 조회합니다. "
                    + "새로고침/크래시 후 재접속 시 이어서 업로드하기 위한 재개 정보입니다."
    )
    // presign/complete와 동일 이중 방어. "현재 녹음자만"은 서비스가 검증한다.
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/status")
    public ApiResponse<PartUploadStatusResponse> status(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId) {
        // 요청자는 JWT principal에서 꺼낸다(presign/complete와 동일 원칙).
        GetPartUploadStatusUseCase.Result result =
                getPartUploadStatusUseCase.getPartUploadStatus(meetingId, memberId);
        return ApiResponse.success("조회 성공", PartUploadStatusResponse.from(result));
    }
}
