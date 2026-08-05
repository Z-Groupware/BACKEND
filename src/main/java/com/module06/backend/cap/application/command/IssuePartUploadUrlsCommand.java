package com.module06.backend.cap.application.command;

public record IssuePartUploadUrlsCommand(
        Long meetingId,
        Long callerId,
        int count,
        String contentType
) {
}
