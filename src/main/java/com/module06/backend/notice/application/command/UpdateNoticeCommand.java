package com.module06.backend.notice.application.command;

/* NOTI-04 공지 수정에 필요한 인증 원본·경로 식별자·전체 수정 본문을 묶는다. */
public record UpdateNoticeCommand(
        Long companyId,
        Long noticeId,
        Long requesterMemberId,
        String requesterRole,
        String title,
        String content
) {
}
