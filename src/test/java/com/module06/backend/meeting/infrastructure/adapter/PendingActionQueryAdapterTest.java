package com.module06.backend.meeting.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * C 액션 도메인 연동 전 Pending Adapter가 존재 여부를 임의로 가정하지 않는지 검증한다.
 */
@DisplayName("MEET-01 ActionQueryPort Pending Adapter")
class PendingActionQueryAdapterTest {

    /* 관련 액션 검증 요청이 들어오면 연동 누락을 명시적으로 드러내는지 확인한다. */
    @Test
    @DisplayName("실제 액션 Adapter가 없으면 명시적 연동 대기 예외를 던진다")
    void failsExplicitlyUntilActionAdapterIsConnected() {
        /* 현재 C도메인 연결 전 사용하는 Pending Adapter를 생성한다. */
        PendingActionQueryAdapter adapter = new PendingActionQueryAdapter();

        /* false를 반환해 AC-001로 위장하지 않고 실제 연동 누락 원인을 알려야 한다. */
        assertThatThrownBy(() -> adapter.existsAction(10L, 305L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ActionQueryPort 연동 대기 중")
                .hasMessageContaining("C(action) 도메인");
    }
}
