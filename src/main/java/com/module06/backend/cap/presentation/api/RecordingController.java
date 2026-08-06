package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.presentation.api.dto.request.ManualRecordingRequest;
import com.module06.backend.cap.presentation.api.dto.request.StartRecordingAssemblyRequest;
import com.module06.backend.cap.presentation.api.dto.response.ManualRecordingResponse;
import com.module06.backend.cap.presentation.api.dto.response.RecordingAssemblyResponse;
import com.module06.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Capture Recording", description = "회의 녹음 조립/등록 API")
@RestController
@RequestMapping("/api/meetings/{meetingId}/recordings")
public class RecordingController {

    private final StartRecordingAssemblyUseCase startRecordingAssemblyUseCase;
    private final RegisterManualRecordingUseCase registerManualRecordingUseCase;

    public RecordingController(StartRecordingAssemblyUseCase startRecordingAssemblyUseCase,
                              RegisterManualRecordingUseCase registerManualRecordingUseCase) {
        this.startRecordingAssemblyUseCase = startRecordingAssemblyUseCase;
        this.registerManualRecordingUseCase = registerManualRecordingUseCase;
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
}
