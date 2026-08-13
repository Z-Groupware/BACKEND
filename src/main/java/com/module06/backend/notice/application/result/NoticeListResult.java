package com.module06.backend.notice.application.result;

import java.time.LocalDateTime;
import java.util.List;

/* NOTI-01 애플리케이션 계층이 프레젠테이션 계층에 반환하는 공지 목록 결과다. */
public record NoticeListResult(List<NoticeItem> notices, Page page) {

    /* null 대신 항상 불변 목록을 제공해 빈 결과도 동일한 응답 구조로 처리한다. */
    public NoticeListResult {
        /* 저장소 결과가 외부에서 변경되지 않도록 방어적 복사한다. */
        notices = List.copyOf(notices);
    }

    /* 공지 목록 한 행의 식별자·제목·생성 시각이다. */
    public record NoticeItem(Long noticeId, String title, LocalDateTime createdAt) {
    }

    /* 현재 페이지와 전체 결과 규모를 나타내는 페이지 메타데이터다. */
    public record Page(int page, int size, long totalElements, int totalPages) {
    }
}
