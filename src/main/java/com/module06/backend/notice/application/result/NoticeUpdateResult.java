package com.module06.backend.notice.application.result;

import java.time.LocalDateTime;

import com.module06.backend.notice.domain.model.Notice;

/* NOTI-04 수정 직후 상세 화면에 제공할 공지 전체 결과다. */
public record NoticeUpdateResult(
        Long noticeId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /* 저장된 공지 도메인 상태를 수정 결과 계약으로 변환한다. */
    public static NoticeUpdateResult from(Notice notice) {
        /* 외부 응답에 작성자·회사·삭제 상태를 노출하지 않고 화면 필드만 전달한다. */
        return new NoticeUpdateResult(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
