package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface GetHandoverListUseCase {

    List<HandoverSummary> list(HandoverListQuery query);

    /**
     * 조회 범위. 클라이언트가 지정하지 못한다 — 컨트롤러가 토큰(역할/isAdmin)으로 서버측에서 결정한다.
     * SELF=본인(멤버), TEAM=자기 팀(리더), COMPANY=회사 전체(오너·어드민).
     */
    enum Scope {
        SELF, TEAM, COMPANY
    }

    /**
     * 스코프별로 딱 하나의 식별자만 채워진다. presence로 스코프를 추론하지 않고 scope를 명시해,
     * "쿼리파라미터로 남의 인수인계를 본다"는 경로를 원천 차단한다.
     */
    record HandoverListQuery(
            Scope scope,
            Long memberId,
            Long teamId,
            Long companyId,
            HandoverStatus status
    ) {
        public static HandoverListQuery self(Long memberId, HandoverStatus status) {
            return new HandoverListQuery(Scope.SELF, memberId, null, null, status);
        }

        public static HandoverListQuery team(Long teamId, HandoverStatus status) {
            return new HandoverListQuery(Scope.TEAM, null, teamId, null, status);
        }

        public static HandoverListQuery company(Long companyId, HandoverStatus status) {
            return new HandoverListQuery(Scope.COMPANY, null, null, companyId, status);
        }
    }

    record HandoverSummary(
            Long id,
            Long writerMemberId,
            String writerName,
            String writerPosition,
            Long teamId,
            HandoverType handoverType,
            HandoverStatus status,
            LocalDateTime leaveStartAt,
            LocalDateTime leaveEndAt,
            LocalDate lastWorkingDay,
            int itemCount,
            int reassignRequiredCount,
            int reassignedCount,
            LocalDateTime finalizedAt,
            String finalApproverName
    ) {
    }
}
