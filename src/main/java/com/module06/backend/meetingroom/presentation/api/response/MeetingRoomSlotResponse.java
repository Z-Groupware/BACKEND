package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomSlotSummary;

/*
 * ROOM-02 응답의 30분 슬롯 한 칸을 표현하는 프레젠테이션 DTO다.
 *
 * status는 프론트엔드의 클릭 가능 판정에 그대로 쓰이며, AVAILABLE 칸이 회의 개설 모달로 이어진다.
 * 예약이 없는 칸은 meetingId·title이 null이고, 예약된 칸이라도 열람 권한이 없으면 title만 null이다.
 *
 * @param startTime 슬롯 시작 시각
 * @param status 슬롯 예약 상태
 * @param meetingId 슬롯을 점유한 회의 식별자
 * @param title 노출 가능한 회의 제목
 */
public record MeetingRoomSlotResponse(
        String startTime,
        String status,
        Long meetingId,
        String title
) {

    /*
     * 애플리케이션 슬롯 결과를 외부 API 응답 항목으로 변환한다.
     *
     * @param summary 변환할 슬롯 조회 결과
     * @return HH:mm 형식의 시작 시각을 포함한 슬롯 응답 항목
     */
    public static MeetingRoomSlotResponse from(MeetingRoomSlotSummary summary) {
        /* 상태 enum은 이름 문자열로, 시각은 API 계약의 HH:mm 문자열로 변환한다. */
        return new MeetingRoomSlotResponse(
                ApiTimeFormat.formatTime(summary.startTime()),
                summary.status().name(),
                summary.meetingId(),
                summary.title()
        );
    }
}
