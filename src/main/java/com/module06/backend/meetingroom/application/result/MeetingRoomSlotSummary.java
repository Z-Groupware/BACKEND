package com.module06.backend.meetingroom.application.result;

import java.time.LocalTime;

import com.module06.backend.meetingroom.domain.model.MeetingRoomSlotStatus;

/*
 * ROOM-02 응답의 30분 슬롯 한 칸을 표현하는 애플리케이션 결과 객체다.
 *
 * 예약이 없는 칸은 meetingId와 title이 모두 null이고,
 * 예약된 칸이라도 요청자가 참석자가 아니면 title만 null로 마스킹된다.
 *
 * @param startTime 슬롯 시작 시각
 * @param status 슬롯 예약 상태
 * @param meetingId 슬롯을 점유한 회의 식별자, 예약이 없으면 null
 * @param title 노출 가능한 회의 제목, 예약이 없거나 열람 권한이 없으면 null
 */
public record MeetingRoomSlotSummary(
        LocalTime startTime,
        MeetingRoomSlotStatus status,
        Long meetingId,
        String title
) {

    /*
     * 예약이 없는 슬롯 결과를 생성한다.
     *
     * @param startTime 슬롯 시작 시각
     * @return 예약 가능 상태의 슬롯 결과
     */
    public static MeetingRoomSlotSummary available(LocalTime startTime) {
        /* 예약 가능 슬롯은 회의 정보가 없으므로 식별자와 제목을 비운다. */
        return new MeetingRoomSlotSummary(startTime, MeetingRoomSlotStatus.AVAILABLE, null, null);
    }

    /*
     * 예약된 슬롯 결과를 생성한다.
     *
     * @param startTime 슬롯 시작 시각
     * @param meetingId 슬롯을 점유한 회의 식별자
     * @param title 마스킹이 적용된 회의 제목
     * @return 예약 상태의 슬롯 결과
     */
    public static MeetingRoomSlotSummary reserved(LocalTime startTime, Long meetingId, String title) {
        /* 제목 마스킹은 이 결과가 만들어지기 전에 결정되므로 전달받은 값을 그대로 사용한다. */
        return new MeetingRoomSlotSummary(startTime, MeetingRoomSlotStatus.RESERVED, meetingId, title);
    }
}
