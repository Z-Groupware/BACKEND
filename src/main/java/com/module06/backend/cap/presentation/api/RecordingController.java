package com.module06.backend.cap.presentation.api;

import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.presentation.api.dto.request.StartRecordingAssemblyRequest;
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

    public RecordingController(StartRecordingAssemblyUseCase startRecordingAssemblyUseCase) {
        this.startRecordingAssemblyUseCase = startRecordingAssemblyUseCase;
    }

    // 녹음 종료(조립 트리거) (CAP-05)
    @Operation(
            summary = "녹음 종료(조립 트리거)",
            description = "세그먼트별 seq 연속성을 검증한 뒤 녹음 조립을 시작합니다. 구멍이 있으면 409로 막습니다. "
                    + "회의 종료(MEET-08)와는 다른 개념이며, 대부분의 회의는 종료 시 자동 조립되므로 이 API는 선택적입니다."
    )
    // presign/complete와 동일 이중 방어. "현재 녹음자만"은 서비스가 검증한다.
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
}
