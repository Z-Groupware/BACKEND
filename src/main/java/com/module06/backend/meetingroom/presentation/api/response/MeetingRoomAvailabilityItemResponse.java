package com.module06.backend.meetingroom.presentation.api.response;

import java.util.List;

import com.module06.backend.meetingroom.application.result.MeetingRoomAvailabilitySummary;

/*
 * ROOM-02 응답에서 회의실 한 곳의 하루 슬롯 현황을 표현하는 프레젠테이션 DTO다.
 *
 * slots에는 이용 가능 시간 안의 칸만 담기므로 프론트엔드가 회색 처리할 칸을 따로 계산하지 않는다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param availableFrom 이용 가능 시작 시각
 * @param availableTo 이용 가능 종료 시각
 * @param slots 30분 슬롯 현황 목록
 */
public record MeetingRoomAvailabilityItemResponse(
        Long meetingRoomId,
        String name,
        String availableFrom,
        String availableTo,
        List<MeetingRoomSlotResponse> slots
) {

    /*
     * 응답 생성 이후 슬롯 목록이 변경되지 않도록 불변으로 복사한다.
     *
     * @param slots 30분 슬롯 응답 항목 목록
     */
    public MeetingRoomAvailabilityItemResponse {
        /* 슬롯이 없는 회의실도 null이 아닌 빈 배열로 직렬화되게 한다. */
        slots = List.copyOf(slots);
    }

    /*
     * 애플리케이션 회의실 현황 결과를 외부 API 응답 항목으로 변환한다.
     *
     * @param summary 변환할 회의실 현황 조회 결과
     * @return 회의실 정보와 슬롯 목록을 담은 응답 항목
     */
    public static MeetingRoomAvailabilityItemResponse from(MeetingRoomAvailabilitySummary summary) {
        /* 회의실 이용 가능 시각과 각 슬롯 시각을 모두 API 계약의 HH:mm 문자열로 변환한다. */
        return new MeetingRoomAvailabilityItemResponse(
                summary.meetingRoomId(),
                summary.name(),
                ApiTimeFormat.formatTime(summary.availableFrom()),
                ApiTimeFormat.formatTime(summary.availableTo()),
                summary.slots().stream()
                        .map(MeetingRoomSlotResponse::from)
                        .toList()
        );
    }
}
