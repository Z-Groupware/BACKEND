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
 * - MeetingRoom: 자신의 이용 가능 시간을 이 규칙으로 분할해 슬롯 시작 시각을 만든다
 * - MeetingRoomAvailabilityService: 응답의 slotMinutes 값으로 SLOT_MINUTES를 내려준다
 */
public final class SlotGrid {

    /* 예약 슬롯 하나의 길이(분)이며 API 응답의 slotMinutes 값과 동일하다. */
    public static final int SLOT_MINUTES = 30;

    /* 초·나노초 정밀도를 잃지 않고 슬롯 길이를 비교하기 위한 30분의 나노초 값이다. */
    private static final long SLOT_NANOS = SLOT_MINUTES * 60L * 1_000_000_000L;

    /*
     * 상수와 정적 계산만 제공하므로 인스턴스 생성을 막는다.
     */
    private SlotGrid() {
    }

    /*
     * 이용 가능 시간을 30분 단위로 균등 분할해 각 슬롯의 시작 시각을 만든다.
     * 슬롯이 종료 시각을 넘기면 그리드가 깨지므로 끝에 남는 30분 미만 구간은 버린다.
     * 예를 들어 09:00~18:00은 09:00부터 17:30까지 18개, 09:00~09:20은 슬롯이 없다.
     *
     * @param availableFrom 회의실 이용 시작 시각
     * @param availableTo 회의실 이용 종료 시각
     * @return 슬롯 시작 시각 목록, 슬롯을 만들 수 없으면 빈 목록
     */
    public static List<LocalTime> slotStarts(LocalTime availableFrom, LocalTime availableTo) {
        /* 이용 가능 시간이 없는 회의실은 그리드를 만들 수 없으므로 빈 목록으로 처리한다. */
        if (availableFrom == null || availableTo == null) {
            return List.of();
        }

        /* 초·나노초를 버리지 않도록 자정 기준 나노초로 환산해 실제 이용 시간 경계를 유지한다. */
        long fromNanoOfDay = availableFrom.toNanoOfDay();
        long toNanoOfDay = availableTo.toNanoOfDay();

        /* 시작 시각부터 슬롯 하나가 종료 시각 안에 완전히 들어가는 동안만 슬롯을 추가한다. */
        List<LocalTime> slotStarts = new ArrayList<>();
        for (long start = fromNanoOfDay; start + SLOT_NANOS <= toNanoOfDay; start += SLOT_NANOS) {
            slotStarts.add(LocalTime.ofNanoOfDay(start));
        }

        /* 계산 결과가 외부에서 변경되지 않도록 불변 목록으로 반환한다. */
        return List.copyOf(slotStarts);
    }

}
