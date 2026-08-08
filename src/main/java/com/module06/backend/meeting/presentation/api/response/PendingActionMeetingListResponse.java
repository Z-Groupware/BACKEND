package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.module06.backend.meeting.application.result.PendingActionMeetingListResult;

/*
 * MEET-10 성공 응답의 data 영역이다.
 *
 * 조회 결과가 없을 때도 meetings를 null이 아닌 빈 배열로 직렬화한다.
 * 검토 화면 이동 URL은 응답에 넣지 않고 프론트가 meetingId로 조립한다.
 */
public record PendingActionMeetingListResponse(List<MeetingResponse> meetings) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 확정 대기 회의 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public PendingActionMeetingListResponse {
        /* 빈 목록은 허용하되 null 결과는 외부 계약 위반으로 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 애플리케이션 결과를 MEET-10 외부 응답 계약으로 변환한다. */
    public static PendingActionMeetingListResponse from(PendingActionMeetingListResult result) {
        /* 저장소가 보장한 최근 회의 순서를 보존하면서 각 회의를 중첩 응답으로 변환한다. */
        List<MeetingResponse> meetings = result.meetings().stream()
                .map(PendingActionMeetingListResponse::toMeetingResponse)
                .toList();

        /* 변환된 확정 대기 회의 배열을 data 영역 응답으로 반환한다. */
        return new PendingActionMeetingListResponse(meetings);
    }

    /* 확정 대기 회의 애플리케이션 결과 한 건을 외부 카드 응답으로 변환한다. */
    private static MeetingResponse toMeetingResponse(PendingActionMeetingListResult.MeetingItem meeting) {
        /* 상태와 일시를 명세 문자열로 바꾸고 프로젝트 중첩 값을 조립한다. */
        return new MeetingResponse(
                meeting.meetingId(),
                meeting.title(),
                meeting.status().name(),
                formatDateTime(meeting.startAt()),
                meeting.pendingActionCount(),
                new ProjectResponse(
                        meeting.project().projectId(),
                        meeting.project().tag(),
                        meeting.project().name()
                )
        );
    }

    /* 필수 로컬 일시를 명세의 초 단위 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* 정상 결과의 필수 시각을 고정 포맷터로 직렬화한다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 마이페이지의 확정 대기 회의 카드 한 건을 나타내는 응답이다. */
    public record MeetingResponse(
            Long meetingId,
            String title,
            String status,
            String startAt,
            long pendingActionCount,
            ProjectResponse project
    ) {
    }

    /* 확정 대기 회의 카드에 표시할 프로젝트 태그 정보다. */
    public record ProjectResponse(Long projectId, String tag, String name) {
    }
}
