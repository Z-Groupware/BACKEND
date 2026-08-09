package com.module06.backend.identity.member.presentation.api.dto.response;

import java.time.LocalDate;

import com.module06.backend.identity.member.application.dto.MemberDetail;

/**
 * 구성원 상세 응답(§7-3). {@code assignedActions}·{@code leaveRequest}·{@code offboardingRequest}
 * 는 담지 않는다 — 액션·인수인계 도메인 연계가 필요해 이번 범위 밖이다.
 */
public record MemberDetailResponse(
        Long memberId,
        String name,
        Long teamId,
        String teamName,
        Long jobPositionId,
        String positionName,
        String role,
        boolean isAdmin,
        String roleLabel,
        String workStatus,
        String email,
        LocalDate joinedOn
) {

    public static MemberDetailResponse from(MemberDetail detail) {
        return new MemberDetailResponse(
                detail.memberId(), detail.name(), detail.teamId(), detail.teamName(),
                detail.jobPositionId(), detail.positionName(), detail.role().name(), detail.isAdmin(),
                detail.roleLabel(), detail.workStatus().name(), detail.email(), detail.joinedOn());
    }
}
