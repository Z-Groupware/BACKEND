package com.module06.backend.notice.application.result;

import java.time.LocalDateTime;

/* NOTI-02 애플리케이션 계층이 반환하는 공지 상세 결과다. */
public record NoticeDetailResult(
        Long noticeId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
