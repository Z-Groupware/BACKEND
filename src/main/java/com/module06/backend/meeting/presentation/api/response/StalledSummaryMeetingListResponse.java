package com.module06.backend.meeting.presentation.api.response;

import java.util.List;

import com.module06.backend.meeting.application.result.StalledSummaryMeetingListResult;

/* MEET-15 성공 응답의 문제 회의 목록과 페이지 메타데이터를 나타낸다. */
public record StalledSummaryMeetingListResponse(
        List<MeetingResponse> meetings,
        PageResponse page
) {

    /* 응답 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public StalledSummaryMeetingListResponse {
        /* 빈 목록은 허용하되 null 목록은 외부 계약 위반으로 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 애플리케이션 결과를 MEET-15 외부 응답 계약으로 변환한다. */
    public static StalledSummaryMeetingListResponse from(StalledSummaryMeetingListResult result) {
        /* D 후보의 최근 순서를 유지하면서 카드 응답으로 변환한다. */
        List<MeetingResponse> meetings = result.meetings().stream()
                .map(meeting -> new MeetingResponse(
                        meeting.meetingId(),
                        meeting.title(),
                        meeting.stalled()
                ))
                .toList();

        /* 애플리케이션 페이지 값을 외부 응답 필드로 그대로 옮긴다. */
        PageResponse page = new PageResponse(
                result.page().page(),
                result.page().size(),
                result.page().totalElements(),
                result.page().totalPages()
        );

        /* 완성된 카드와 페이지 메타를 data 영역 응답으로 반환한다. */
        return new StalledSummaryMeetingListResponse(meetings, page);
    }

    /* 요약 중단·실패 회의 카드 한 건의 외부 응답이다. */
    public record MeetingResponse(Long meetingId, String title, boolean isStalled) {
    }

    /* 페이지 이동과 섹션 전체 건수 표시를 위한 외부 응답이다. */
    public record PageResponse(int page, int size, long totalElements, int totalPages) {
    }
}
