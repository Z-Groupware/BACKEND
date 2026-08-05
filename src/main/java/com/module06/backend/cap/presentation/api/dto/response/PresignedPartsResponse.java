package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.IssuePartUploadUrlsUseCase;

import java.util.List;

// presign 응답 JSON
public record PresignedPartsResponse(
        int segmentSeq,
        List<PartResponse> parts
) {

    // usecase 결과 → 응답 DTO
    public static PresignedPartsResponse from(IssuePartUploadUrlsUseCase.Result result) {
        return new PresignedPartsResponse(
                result.segmentSeq(),
                result.parts().stream().map(PartResponse::from).toList()
        );
    }

    public record PartResponse(int seq, String presignedUrl, int expiresIn) {
        // usecase의 Part 하나 → 응답 DTO
        public static PartResponse from(IssuePartUploadUrlsUseCase.Part part) {
            return new PartResponse(part.seq(), part.presignedUrl(), part.expiresInSeconds());
        }
    }
}
