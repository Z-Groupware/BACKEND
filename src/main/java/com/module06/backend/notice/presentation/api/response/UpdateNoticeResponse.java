package com.module06.backend.notice.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.notice.application.result.NoticeUpdateResult;

/* NOTI-04 수정 성공 응답으로 상세 화면에 필요한 최종 공지 전체를 제공한다. */
public record UpdateNoticeResponse(
        Long noticeId,
        String title,
        String content,
        String createdAt,
        String updatedAt
) {

    /* 외부 계약의 초 단위 오프셋 없는 KST 로컬 일시 포맷터다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 수정 결과를 NOTI-04 외부 응답 계약으로 변환한다. */
    public static UpdateNoticeResponse from(NoticeUpdateResult result) {
        /* 수정 후에는 두 일시가 모두 존재하므로 초 단위 문자열로 변환한다. */
        return new UpdateNoticeResponse(
                result.noticeId(),
                result.title(),
                result.content(),
                formatDateTime(result.createdAt()),
                formatDateTime(result.updatedAt())
        );
    }

    /* 필수 공지 일시를 API 고정 문자열 형식으로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* 저장된 KST 로컬 일시를 타임존 변환 없이 명세 문자열로 포맷한다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
