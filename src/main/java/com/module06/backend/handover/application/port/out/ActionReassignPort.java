package com.module06.backend.handover.application.port.out;

import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.util.List;

/**
 * C(액션) 모듈 소유 아웃 포트 — 인수인계 베이스(생성 스냅샷·완료 커밋)용.
 * (인사이트 레이어가 findHandoverableActions(Long)/findTeamActionsForDeparture 및 record 필드를 증분 추가한다.)
 */
public interface ActionReassignPort {

    List<HandoverableAction> findHandoverableActions(Long memberId, HandoverType type);

    void reassign(Long actionId, Long fromMemberId, Long toMemberId);

    record HandoverableAction(
            Long actionId,
            String title,
            String projectTag,
            String actionType,
            String status,
            LocalDate deadline,
            Long sourceMeetingId,
            String sourceMeetingTitle,
            String content
    ) {
    }
}
