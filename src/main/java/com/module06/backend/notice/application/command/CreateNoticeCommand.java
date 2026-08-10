package com.module06.backend.notice.application.command;

/* NOTI-03 공지 작성에 필요한 인증 정보와 제목·본문을 전달하는 Command다. */
public record CreateNoticeCommand(
        Long companyId,
        Long requesterMemberId,
        String requesterRole,
        boolean requesterAdmin,
        String title,
        String content
) {
}
