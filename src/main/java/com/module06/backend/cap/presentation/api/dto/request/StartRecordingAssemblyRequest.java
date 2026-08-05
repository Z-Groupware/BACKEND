package com.module06.backend.cap.presentation.api.dto.request;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// 녹음 종료(조립) 요청 body. 컨트롤러의 path/토큰과 합쳐 Command로 변환한다.
// 필수 숫자 필드는 CompletePartRequest와 동일하게 박싱 타입 + @NotNull로 받는다 — primitive면 누락 시
// 조용히 0이 돼(빈 녹음 조립으로 샘) 필드 존재 자체를 강제하지 못한다.
public record StartRecordingAssemblyRequest(
        @NotNull @PositiveOrZero Integer lastSegmentSeq,
        @NotNull @PositiveOrZero Integer lastSeq
) {

    public StartRecordingAssemblyCommand toCommand(Long meetingId, Long callerId) {
        return new StartRecordingAssemblyCommand(meetingId, callerId, lastSegmentSeq, lastSeq);
    }
}
