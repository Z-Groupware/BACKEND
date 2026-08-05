package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;

import java.util.List;

// 녹음 종료(조립) 응답 JSON
public record RecordingAssemblyResponse(
        String status,
        List<Integer> missingSeqs
) {

    // usecase 결과 → 응답 DTO
    public static RecordingAssemblyResponse from(StartRecordingAssemblyUseCase.Result result) {
        return new RecordingAssemblyResponse(result.status(), result.missingSeqs());
    }
}
