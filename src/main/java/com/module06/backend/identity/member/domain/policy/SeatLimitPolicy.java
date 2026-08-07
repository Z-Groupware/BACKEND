package com.module06.backend.identity.member.domain.policy;

import org.springframework.stereotype.Component;

import com.module06.backend.identity.member.domain.model.Plan;

/**
 * 계정 발급 좌석 상한(§5-1 검증 5). Free 5석 · Team(유료) 10석 고정이다 — 좌석 수를 요금제별로
 * 조절하는 화면은 아직 없으므로 상수로 둔다. 구독이 없는 회사(온보딩 전)는 FREE 로 본다.
 */
@Component
public class SeatLimitPolicy {

    private static final int FREE_SEATS = 5;
    private static final int TEAM_SEATS = 10;

    /** {@code plan} 이 null 이면(살아 있는 구독 없음) FREE 상한을 적용한다. */
    public int maxSeats(Plan plan) {
        if (plan == Plan.TEAM) {
            return TEAM_SEATS;
        }
        return FREE_SEATS;
    }
}
