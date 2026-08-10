package com.module06.backend.notice.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.notice.application.result.NoticeDetailResult;

/* NOTI-02 성공 응답의 data 영역으로 공지 제목·본문·생명주기 시각을 제공한다. */
public record NoticeDetailResponse(
        Long noticeId,
        String title,
        String content,
        String createdAt,
        String updatedAt
) {

    /* 명세가 요구하는 초 단위 오프셋 없는 KST 로컬 일시 포맷터다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 상세 결과를 NOTI-02 외부 응답 계약으로 변환한다. */
    public static NoticeDetailResponse from(NoticeDetailResult result) {
        /* 수정 전 공지의 updatedAt은 null을 유지하고 나머지 일시는 고정 문자열로 변환한다. */
        return new NoticeDetailResponse(
                result.noticeId(),
                result.title(),
                result.content(),
                formatDateTime(result.createdAt()),
                formatNullableDateTime(result.updatedAt())
        );
    }

    /* 필수 공지 생성 일시를 API 고정 문자열 형식으로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* 생성 일시는 저장된 모든 공지에 존재하므로 그대로 포맷한다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 선택 수정 일시를 null 보존 문자열 형식으로 변환한다. */
    private static String formatNullableDateTime(LocalDateTime dateTime) {
        /* 수정되지 않은 공지는 null이고 수정된 공지만 초 단위 문자열을 반환한다. */
        return dateTime == null ? null : formatDateTime(dateTime);
    }
}
