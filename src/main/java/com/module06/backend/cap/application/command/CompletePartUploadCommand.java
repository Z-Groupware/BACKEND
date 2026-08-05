package com.module06.backend.cap.application.command;

public record CompletePartUploadCommand(
        Long meetingId,
        Long callerId,
        int segmentSeq,
        int seq,
        String s3Key,
        long sizeBytes
) {
}
