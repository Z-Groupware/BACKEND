package com.module06.backend.meetingroom.domain.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

/*
 * 이미 회의가 점유한 30분 슬롯 하나를 표현하는 도메인 값 객체다.
 *
 * meeting_room_slot 테이블의 한 행에 대응하며, 회의 제목은 현황판 응답을 만들 때만 필요한 표시 값이다.
 * 회의 애그리거트를 직접 참조하지 않고 식별자와 제목만 값으로 들고 있어 도메인 사이의 결합을 만들지 않는다.
 *
 * 연결된 클래스
 * - MeetingRoomSlotRepository: 이 값 객체 목록을 반환하는 도메인 저장소 계약
 * - MeetingRoomAvailabilityService: 슬롯 상태와 제목 마스킹을 적용해 응답을 조립한다
 *
 * @param meetingRoomId 슬롯을 점유한 회의실 식별자
 * @param slotStart 슬롯 시작 일시
 * @param meetingId 슬롯을 점유한 회의 식별자
 * @param meetingTitle 슬롯을 점유한 회의 제목
 */
public record ReservedSlot(
        Long meetingRoomId,
        LocalDateTime slotStart,
        Long meetingId,
        String meetingTitle
) {

    /*
     * 슬롯 시작 일시에서 시간표 그리드의 열에 해당하는 시각만 얻는다.
     *
     * @return 슬롯 시작 시각
     */
    public LocalTime startTime() {
        /* 현황 조회는 하루 단위이므로 날짜를 제외한 시각만 슬롯 위치를 식별하는 값으로 사용한다. */
        return slotStart.toLocalTime();
    }

    /*
     * 요청자에게 노출할 회의 제목을 결정한다.
     * 현황판은 회사 전체가 함께 보지만 회의 제목은 열람 권한을 따르므로,
     * 참석자가 아닌 요청자에게는 제목을 숨기고 예약되었다는 사실만 남긴다.
     *
     * @param requesterIsAttendee 요청자가 이 회의의 참석자인지 여부
     * @return 참석자면 회의 제목, 참석자가 아니면 null
     */
    public String titleFor(boolean requesterIsAttendee) {
        /* 열람 권한이 없으면 제목을 null로 마스킹해 예약 여부만 공개한다. */
        return requesterIsAttendee ? meetingTitle : null;
    }
}
