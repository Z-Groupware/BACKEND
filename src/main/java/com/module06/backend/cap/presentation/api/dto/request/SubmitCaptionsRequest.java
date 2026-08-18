package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

// 자막 청크 배치 요청 body(JSON)를 그대로 받는 DTO. 컨트롤러의 path/principal 값과 합쳐 Command로 변환한다.
// rms는 여기서 @NotNull을 걸지 않는다 — 없으면 400이 아니라 422(MEETING_422_2)로 응답해야 해서,
// 서비스/도메인 계층(CaptionChunk 생성자)에서 CAP_RMS_REQUIRED로 판정한다.
public record SubmitCaptionsRequest(
        @NotEmpty List<@Valid ChunkRequest> chunks
) {
    public record ChunkRequest(
            @NotNull Integer seq,
            @NotNull Integer startMs,
            @NotNull Integer endMs,
            @NotBlank String text,
            BigDecimal rms
    ) {
    }

    public SubmitCaptionsCommand toCommand(Long meetingId, Long memberId) {
        List<SubmitCaptionsCommand.ChunkInput> chunkInputs = chunks.stream()
                .map(chunk -> new SubmitCaptionsCommand.ChunkInput(
                        chunk.seq(), chunk.startMs(), chunk.endMs(), chunk.text(), chunk.rms()))
                .toList();
        return new SubmitCaptionsCommand(meetingId, memberId, chunkInputs);
    }
}
