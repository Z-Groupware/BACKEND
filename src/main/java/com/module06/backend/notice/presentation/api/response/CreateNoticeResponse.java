package com.module06.backend.notice.presentation.api.response;

import com.module06.backend.notice.application.result.NoticeCreationResult;

/* NOTI-03 작성 성공 응답에서 생성된 공지 식별자만 제공한다. */
public record CreateNoticeResponse(Long noticeId) {

    /* 애플리케이션 생성 결과를 외부 응답 계약으로 변환한다. */
    public static CreateNoticeResponse from(NoticeCreationResult result) {
        /* 작성 직후 목록 이동에 필요한 공지 식별자만 반환한다. */
        return new CreateNoticeResponse(result.noticeId());
    }
}
