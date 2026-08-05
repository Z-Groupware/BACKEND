package com.module06.backend.meeting.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 회의 예약 구간을 30분 단위 슬롯 시작 시각으로 변환하는 도메인 계산기다.
 *
 * 애플리케이션에서 시각 그리드 검증을 마친 뒤 호출하며, 만들어진 각 시각은
 * meeting_room_slot 복합 PK의 slot_start 값으로 사용된다.
 */
public final class MeetingSlotGrid {

    /* 회의 예약과 회의실 현황이 공통으로 사용하는 슬롯 길이다. */
    public static final int SLOT_MINUTES = 30;

    /* 상태가 없는 계산 전용 클래스이므로 인스턴스 생성을 막는다. */
    private MeetingSlotGrid() {
    }

    /*
     * 시작 시각 이상, 종료 시각 미만의 모든 30분 슬롯 시작 시각을 만든다.
     *
     * @param startAt 예약 시작 일시
     * @param endAt 예약 종료 일시
     * @return 예약이 점유해야 하는 정렬된 슬롯 시작 시각 목록
     */
    public static List<LocalDateTime> slotStarts(LocalDateTime startAt, LocalDateTime endAt) {
        /* 호출자가 넘긴 시간 범위를 변경하지 않고 순서대로 슬롯을 누적한다. */
        List<LocalDateTime> slots = new ArrayList<>();

        /* 종료 시각은 다음 예약이 사용할 수 있으므로 포함하지 않는다. */
        for (LocalDateTime slot = startAt; slot.isBefore(endAt); slot = slot.plusMinutes(SLOT_MINUTES)) {
            slots.add(slot);
        }

        /* 외부에서 예약 슬롯 집합을 변경하지 못하도록 불변 목록을 반환한다. */
        return List.copyOf(slots);
    }
}
