package com.module06.backend.notice.application.query;

/* NOTI-01 목록 조회에 필요한 인증 회사 식별자를 전달하는 Query다. */
public record GetNoticeListQuery(Long companyId) {
}
