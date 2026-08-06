package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.DeleteRecordingUseCase;
import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.presentation.api.dto.request.ManualRecordingRequest;
import com.module06.backend.cap.presentation.api.dto.request.StartRecordingAssemblyRequest;
import com.module06.backend.cap.presentation.api.dto.response.DeleteRecordingResponse;
import com.module06.backend.cap.presentation.api.dto.response.ManualRecordingResponse;
import com.module06.backend.cap.presentation.api.dto.response.PlaybackUrlResponse;
import com.module06.backend.cap.presentation.api.dto.response.RecordingAssemblyResponse;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Capture Recording", description = "회의 녹음 조립/등록 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/recordings")
public class RecordingController {

    private final StartRecordingAssemblyUseCase startRecordingAssemblyUseCase;
    private final RegisterManualRecordingUseCase registerManualRecordingUseCase;
    private final GetPlaybackUrlUseCase getPlaybackUrlUseCase;
    private final DeleteRecordingUseCase deleteRecordingUseCase;

    public RecordingController(StartRecordingAssemblyUseCase startRecordingAssemblyUseCase,
                              RegisterManualRecordingUseCase registerManualRecordingUseCase,
                              GetPlaybackUrlUseCase getPlaybackUrlUseCase,
                              DeleteRecordingUseCase deleteRecordingUseCase) {
        this.startRecordingAssemblyUseCase = startRecordingAssemblyUseCase;
        this.registerManualRecordingUseCase = registerManualRecordingUseCase;
        this.getPlaybackUrlUseCase = getPlaybackUrlUseCase;
        this.deleteRecordingUseCase = deleteRecordingUseCase;
    }

    // 녹음 종료(조립 트리거) (CAP-05)
    @Operation(
            summary = "녹음 종료(조립 트리거)",
            description = "세그먼트별 seq 연속성을 검증한 뒤 녹음 조립을 시작합니다. 구멍이 있으면 409로 막습니다. "
                    + "회의 종료(MEET-08)와는 다른 개념이며, 대부분의 회의는 종료 시 자동 조립되므로 이 API는 선택적입니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RecordingAssemblyResponse> assemble(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody StartRecordingAssemblyRequest request) {
        // 요청자는 JWT principal에서 꺼낸다(presign/complete와 동일 원칙).
        StartRecordingAssemblyUseCase.Result result =
                startRecordingAssemblyUseCase.startRecordingAssembly(request.toCommand(meetingId, memberId));
        return ApiResponse.accepted("녹음 조립을 시작합니다.", RecordingAssemblyResponse.from(result));
    }

    // 녹음파일 수동 업로드 (CAP-10)
    @Operation(
            summary = "녹음파일 수동 업로드(대체 경로)",
            description = "[녹음] 버튼 대신 외부(온라인 회의 등)에서 녹음한 파일을 직접 첨부하는 대체 경로입니다. "
                    + "완료된 파일의 메타데이터를 등록하고 단일 블록 STT를 트리거합니다. 회의 담당자(Host)만 가능합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @PostMapping("/manual")
    public ApiResponse<ManualRecordingResponse> manual(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody ManualRecordingRequest request) {
        // 요청자는 JWT principal에서 꺼낸다(presign/complete와 동일 원칙).
        RegisterManualRecordingUseCase.Result result =
                registerManualRecordingUseCase.registerManualRecording(request.toCommand(meetingId, memberId));
        return ApiResponse.success("녹음 파일이 등록되었습니다.", ManualRecordingResponse.from(result));
    }

    // 재생용 Presigned GET URL 발급 (CAP-14)
    @Operation(
            summary = "재생용 Presigned GET URL 발급",
            description = "녹음본을 재생할 수 있는 presigned GET URL(HTTP Range 지원, 만료 3시간)을 발급합니다. "
                    + "회의 참석자, 또는 같은 회사의 owner/admin만 가능합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/playback-url")
    public ApiResponse<PlaybackUrlResponse> playbackUrl(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @AuthenticationPrincipal(expression = "role") String role,
            @AuthenticationPrincipal(expression = "isAdmin") boolean isAdmin) {
        // 요청자 신원은 JWT principal에서 꺼낸다. 열람 권한(참석자 or 같은 회사 owner/admin)은 서비스가 판정.
        GetPlaybackUrlUseCase.Requester requester =
                new GetPlaybackUrlUseCase.Requester(memberId, companyId, role, isAdmin);
        GetPlaybackUrlUseCase.Result result = getPlaybackUrlUseCase.getPlaybackUrl(meetingId, requester);
        return ApiResponse.success("재생 URL이 발급되었습니다.", PlaybackUrlResponse.from(result));
    }

    // 녹음 삭제 (CAP-15)
    @Operation(
            summary = "녹음 삭제",
            description = "녹음본(S3 오디오·recording·잔여 recording_part)을 하드 삭제합니다. 되돌릴 수 없습니다. "
                    + "owner/admin만 가능하며, STT·분석이 끝나지 않았으면 409로 막고 ?confirm=true로만 강행합니다."
    )
    // 이 엔드포인트만 실제 role gate — owner/admin만. 같은 회사 검증은 서비스가 담당.
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @DeleteMapping
    public ApiResponse<DeleteRecordingResponse> delete(
            @Parameter(description = "회의 ID") @PathVariable Long meetingId,
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @RequestParam(name = "confirm", required = false, defaultValue = "false") boolean confirm) {
        // 회사는 토큰에서, 미확인 STT 강행 여부는 ?confirm 쿼리로. 실제 삭제 가부는 서비스가 판정.
        DeleteRecordingUseCase.Result result = deleteRecordingUseCase.deleteRecording(meetingId, companyId, confirm);
        return ApiResponse.success("녹음 파일이 삭제되었습니다.", DeleteRecordingResponse.from(result));
    }
}
