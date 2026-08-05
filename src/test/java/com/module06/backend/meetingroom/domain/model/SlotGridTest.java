package com.module06.backend.meetingroom.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * 30분 슬롯 그리드 계산 규칙을 검증하는 단위 테스트다.
 *
 * 이 계산이 어긋나면 ROOM-02 현황판의 칸 수와 MEET-01이 점유할 슬롯 수가 달라지므로
 * 경계값(이용 시간의 끝, 30분 미만 구간, 잘못된 시간 범위)을 중심으로 확인한다.
 */
@DisplayName("ROOM-02 30분 슬롯 그리드")
class SlotGridTest {

    /*
     * 이용 가능 시간이 30분으로 균등 분할되고 종료 시각이 슬롯 시작에서 제외되는지 검증한다.
     */
    @Test
    @DisplayName("이용 가능 시간을 30분 단위로 분할하고 종료 시각은 슬롯 시작에서 제외한다")
    void splitsAvailableTimeIntoThirtyMinuteSlots() {
        /* 09:00~11:00을 30분으로 나눈 슬롯 시작 시각을 계산한다. */
        List<LocalTime> slotStarts = SlotGrid.slotStarts(LocalTime.of(9, 0), LocalTime.of(11, 0));

        /* 11:00에 시작하는 슬롯은 이용 시간을 넘기므로 10:30이 마지막 슬롯이어야 한다. */
        assertThat(slotStarts).containsExactly(
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                LocalTime.of(10, 0),
                LocalTime.of(10, 30)
        );
    }

    /*
     * 하루 종일 이용 가능한 회의실의 슬롯 개수를 검증한다.
     */
    @Test
    @DisplayName("09:00~18:00 회의실은 18개 슬롯을 만든다")
    void createsEighteenSlotsForDefaultAvailableTime() {
        /* 기본 이용 시간대의 슬롯을 계산한다. */
        List<LocalTime> slotStarts = SlotGrid.slotStarts(LocalTime.of(9, 0), LocalTime.of(18, 0));

        /* 9시간을 30분으로 나눈 18개 칸이 만들어지고 마지막 칸은 17:30이어야 한다. */
        assertThat(slotStarts).hasSize(18);
        assertThat(slotStarts.get(17)).isEqualTo(LocalTime.of(17, 30));
    }

    /*
     * 끝에 남는 30분 미만 구간이 슬롯으로 만들어지지 않는지 검증한다.
     */
    @Test
    @DisplayName("남는 30분 미만 구간은 슬롯으로 만들지 않는다")
    void ignoresRemainderShorterThanSlot() {
        /* 09:00~10:20처럼 마지막 20분이 남는 이용 시간을 계산한다. */
        List<LocalTime> slotStarts = SlotGrid.slotStarts(LocalTime.of(9, 0), LocalTime.of(10, 20));

        /* 10:00 슬롯은 10:30에 끝나 이용 시간을 넘기므로 09:00·09:30만 남아야 한다. */
        assertThat(slotStarts).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    /*
     * 슬롯을 만들 수 없는 이용 시간에서 빈 목록이 반환되는지 검증한다.
     */
    @Test
    @DisplayName("종료 시각이 시작 시각보다 빠르거나 시각이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenSlotCannotBeCreated() {
        /* 종료 시각이 더 빠른 경우와 시각이 없는 경우를 각각 계산한다. */
        assertThat(SlotGrid.slotStarts(LocalTime.of(18, 0), LocalTime.of(9, 0))).isEmpty();
        assertThat(SlotGrid.slotStarts(LocalTime.of(9, 0), LocalTime.of(9, 20))).isEmpty();
        assertThat(SlotGrid.slotStarts(null, LocalTime.of(18, 0))).isEmpty();
        assertThat(SlotGrid.slotStarts(LocalTime.of(9, 0), null)).isEmpty();
    }
}
