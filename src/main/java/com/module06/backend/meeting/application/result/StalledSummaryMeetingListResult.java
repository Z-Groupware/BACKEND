package com.module06.backend.meeting.application.result;

import java.util.List;

/*
 * MEET-15 조회 결과를 프레젠테이션 계층에 전달하는 애플리케이션 결과 객체다.
 */
public record StalledSummaryMeetingListResult(
        List<MeetingItem> meetings,
        Page page
) {

    /* 결과 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public StalledSummaryMeetingListResult {
        /* 빈 목록은 허용하되 null 목록은 계약 위반으로 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 마이페이지의 요약 중단·실패 회의 카드 한 건이다. */
    public record MeetingItem(Long meetingId, String title, boolean stalled) {
    }

    /* 현재 페이지와 전체 문제 회의 수를 전달하는 페이지 메타데이터다. */
    public record Page(int page, int size, long totalElements, int totalPages) {
    }
}
