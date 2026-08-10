package com.module06.backend.notice.application.query;

/* NOTI-02 상세 조회에 필요한 인증 회사와 공지 식별자를 전달하는 Query다. */
public record GetNoticeDetailQuery(Long companyId, Long noticeId) {
}
