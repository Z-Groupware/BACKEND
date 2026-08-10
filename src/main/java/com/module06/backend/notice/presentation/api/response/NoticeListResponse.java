package com.module06.backend.notice.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.module06.backend.notice.application.result.NoticeListResult;

/* NOTI-01 성공 응답의 data 영역으로 공지 목록을 항상 배열 형태로 제공한다. */
public record NoticeListResponse(List<NoticeResponse> notices) {

    /* 명세가 요구하는 초 단위 오프셋 없는 KST 로컬 일시 포맷터다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* null 목록을 허용하지 않고 생성 이후 변경할 수 없는 응답 배열로 만든다. */
    public NoticeListResponse {
        /* 빈 목록은 유지하면서 외부의 목록 변경은 차단한다. */
        notices = List.copyOf(notices);
    }

    /* 애플리케이션 결과를 NOTI-01 외부 응답 계약으로 변환한다. */
    public static NoticeListResponse from(NoticeListResult result) {
        /* 저장소 최신순을 유지하며 일시를 고정 문자열 형식으로 변환한다. */
        List<NoticeResponse> notices = result.notices().stream()
                .map(notice -> new NoticeResponse(
                        notice.noticeId(),
                        notice.title(),
                        formatDateTime(notice.createdAt())
                ))
                .toList();

        /* 조회 결과가 없을 때도 빈 notices 배열을 가진 응답을 반환한다. */
        return new NoticeListResponse(notices);
    }

    /* 공지 생성 일시를 API 고정 문자열 형식으로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* DATETIME과 일대일인 오프셋 없는 초 단위 문자열을 반환한다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 공지 목록 한 행에 공개하는 식별자·제목·생성 일시다. */
    public record NoticeResponse(Long noticeId, String title, String createdAt) {
    }
}
