package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.GetPartUploadStatusUseCase;

import java.util.List;

// 청크 업로드 상태 조회 응답 JSON
public record PartUploadStatusResponse(
        int segmentSeq,
        int lastSeq,
        List<Integer> missingSeqs,
        int blocksFormed,
        int resumeFromSeq,
        long gapMs
) {

    // usecase 결과 → 응답 DTO
    public static PartUploadStatusResponse from(GetPartUploadStatusUseCase.Result result) {
        return new PartUploadStatusResponse(
                result.segmentSeq(),
                result.lastSeq(),
                result.missingSeqs(),
                result.blocksFormed(),
                result.resumeFromSeq(),
                result.gapMs());
    }
}
