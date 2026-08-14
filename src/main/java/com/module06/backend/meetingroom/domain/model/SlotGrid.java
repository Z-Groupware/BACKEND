package com.module06.backend.meetingroom.domain.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 회의실 예약의 30분 슬롯 그리드를 계산하는 도메인 규칙이다.
 *
 * 예약 단위는 화면의 30분 시간표 그리드이며, 예약 중복 방지도 30분 슬롯 행의 복합 PK로 처리한다.
 * 즉 슬롯 길이는 회의실 현황 조회(ROOM-02)와 회의 개설(MEET-01)이 공유하는 단일 기준이므로
 * 상수와 계산 규칙을 한 곳에 모아 두 기능이 서로 다른 그리드를 쓰는 상황을 원천 차단한다.
 *
 * 연결된 클래스
 * - MeetingRoom: 모든 회의실에 공통인 하루 전체 슬롯 시작 시각을 만든다
 * - MeetingRoomAvailabilityService: 응답의 slotMinutes 값으로 SLOT_MINUTES를 내려준다
 */
public final class SlotGrid {

    /* 예약 슬롯 하나의 길이(분)이며 API 응답의 slotMinutes 값과 동일하다. */
    public static final int SLOT_MINUTES = 30;

    /* 하루를 30분 단위로 나눈 24시간 슬롯 개수다. */
    public static final int DAILY_SLOT_COUNT = 24 * 60 / SLOT_MINUTES;

    /*
     * 상수와 정적 계산만 제공하므로 인스턴스 생성을 막는다.
     */
    private SlotGrid() {
    }

    /*
     * 00:00부터 23:30까지 하루 전체를 30분 단위로 분할한다.
     *
     * @return 정렬된 24시간 슬롯 시작 시각 48개
     */
    public static List<LocalTime> slotStarts() {
        /* 자정을 기준으로 슬롯 번호만큼 30분씩 증가시켜 전체 그리드를 만든다. */
        List<LocalTime> slotStarts = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < DAILY_SLOT_COUNT; slotIndex++) {
            slotStarts.add(LocalTime.MIDNIGHT.plusMinutes((long) slotIndex * SLOT_MINUTES));
        }

        /* 계산 결과가 외부에서 변경되지 않도록 불변 목록으로 반환한다. */
        return List.copyOf(slotStarts);
    }

}
