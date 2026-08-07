package com.module06.backend.handover.application.port.out;

import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C(액션) 모듈 소유 아웃 포트 — 인수인계 베이스(생성 스냅샷·완료 커밋) + 인사이트("레거시 컴파일러")용.
 */
public interface ActionReassignPort {

    List<HandoverableAction> findHandoverableActions(Long memberId, HandoverType type);

    void reassign(Long actionId, Long fromMemberId, Long toMemberId);

    // --- 인사이트 레이어 증분 계약 ---

    /** 인사이트 조립용: 퇴사자 개인 담당 인계 대상 액션(타입 무관). */
    List<HandoverableAction> findHandoverableActions(Long memberId);

    /** 인사이트 조립용: 퇴사자가 관여한 팀 액션(고아 경보 판정 소스). */
    List<TeamActionForDeparture> findTeamActionsForDeparture(Long memberId);

    record HandoverableAction(
            Long actionId,
            String title,
            String projectTag,
            Long projectId,
            String actionType,
            String status,
            LocalDate deadline,
            LocalDateTime actionCreatedAt,
            Long sourceMeetingId,
            String sourceMeetingTitle,
            String content
    ) {
    }

    record TeamActionForDeparture(
            Long actionId,
            String title,
            Long projectId,
            Long sourceMeetingId,
            String status,
            Long teamId
    ) {
    }
}
