package com.module06.backend.meetingroom.application.result;

import java.time.LocalTime;
import java.util.List;

/*
 * ROOM-02 응답에서 회의실 한 곳의 하루 슬롯 현황을 표현하는 애플리케이션 결과 객체다.
 *
 * slots에는 이용 가능 시간 안의 칸만 담기므로, 프론트엔드가 회색 처리할 칸을 따로 계산하지 않는다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param availableFrom 이용 가능 시작 시각
 * @param availableTo 이용 가능 종료 시각
 * @param slots 이용 가능 시간을 30분으로 분할한 슬롯 현황
 */
public record MeetingRoomAvailabilitySummary(
        Long meetingRoomId,
        String name,
        LocalTime availableFrom,
        LocalTime availableTo,
        List<MeetingRoomSlotSummary> slots
) {

    /*
     * 슬롯 목록을 불변으로 복사해 결과 생성 이후 변경되지 않도록 보호한다.
     *
     * @param slots 회의실의 슬롯 현황 목록
     */
    public MeetingRoomAvailabilitySummary {
        /* 조립 과정에서 사용한 가변 목록이 결과 객체에 그대로 남지 않도록 복사한다. */
        slots = List.copyOf(slots);
    }
}
