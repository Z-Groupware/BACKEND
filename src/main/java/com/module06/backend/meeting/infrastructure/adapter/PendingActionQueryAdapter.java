package com.module06.backend.meeting.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.module06.backend.meeting.application.port.out.ActionQueryPort;

/*
 * 액션 도메인의 존재 검증 Port가 연결되기 전까지 사용하는 명시적 pending 어댑터다.
 *
 * relatedActionId가 없는 기본 예약에서는 호출되지 않으며, 값이 있을 때만 연동 필요성을 드러낸다.
 */
@Component
public class PendingActionQueryAdapter implements ActionQueryPort {

    /* 액션 담당 도메인의 회사 범위 조회 계약이 아직 연결되지 않았음을 알린다. */
    @Override
    public boolean existsAction(Long companyId, Long actionId) {
        /* 존재를 임의로 가정하지 않아 고아 related_action_id가 저장되는 것을 막는다. */
        throw new UnsupportedOperationException(
                "ActionQueryPort 연동 대기 중입니다. C(action) 도메인의 회사 범위 조회 구현이 필요합니다."
        );
    }
}
