package com.module06.backend.cap.application.command;

import java.math.BigDecimal;
import java.util.List;

public record SubmitCaptionsCommand(
        Long meetingId,
        Long memberId,
        List<ChunkInput> chunks
) {
    public record ChunkInput(int seq, int startMs, int endMs, String text, BigDecimal rms) {
    }
}
