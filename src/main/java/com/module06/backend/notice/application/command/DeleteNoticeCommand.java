package com.module06.backend.notice.application.command;

/* NOTI-05 공지 삭제에 필요한 인증 원본과 경로 식별자를 묶는다. */
public record DeleteNoticeCommand(
        Long companyId,
        Long noticeId,
        Long requesterMemberId,
        String requesterRole
) {
}
