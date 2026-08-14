package com.module06.backend.meetingroom.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * 24시간 회의실의 30분 슬롯 그리드 계산 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("ROOM-02 24시간 30분 슬롯 그리드")
class SlotGridTest {

    /* 하루가 자정부터 23시 30분까지 빠짐없이 분할되는지 검증한다. */
    @Test
    @DisplayName("하루 24시간을 30분 단위 48개 슬롯으로 반환한다")
    void createsFortyEightSlotsForWholeDay() {
        /* 회의실별 운영 시간 없이 공통 일일 슬롯을 계산한다. */
        List<LocalTime> slotStarts = SlotGrid.slotStarts();

        /* 첫 칸과 마지막 칸 및 전체 개수가 24시간 계약과 일치해야 한다. */
        assertThat(slotStarts).hasSize(48);
        assertThat(slotStarts.get(0)).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(slotStarts.get(47)).isEqualTo(LocalTime.of(23, 30));
    }

    /* 반환 목록이 정확히 30분 간격을 유지하는지 검증한다. */
    @Test
    @DisplayName("모든 슬롯은 이전 슬롯보다 정확히 30분 뒤다")
    void keepsThirtyMinuteIntervals() {
        /* 공통 일일 슬롯 목록을 조회한다. */
        List<LocalTime> slotStarts = SlotGrid.slotStarts();

        /* 두 번째 칸부터 직전 칸에 30분을 더한 값과 같아야 한다. */
        for (int index = 1; index < slotStarts.size(); index++) {
            assertThat(slotStarts.get(index)).isEqualTo(slotStarts.get(index - 1).plusMinutes(30));
        }
    }
}
