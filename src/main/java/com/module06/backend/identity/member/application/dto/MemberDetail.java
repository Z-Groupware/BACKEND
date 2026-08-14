package com.module06.backend.identity.member.application.dto;

import java.time.LocalDate;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;

/**
 * 구성원 상세(§7-3). {@code phone} 은 담지 않는다 — 상세 화면에 표시되는 건
 * 이메일·부서·직급·입사일 4개뿐이다. {@code assignedActions}·{@code leaveRequest}·
 * {@code offboardingRequest} 도 담지 않는다 — 액션·인수인계 도메인 연계가 필요한데 이번 범위 밖이다
 * (leave-approval·offboarding-approval 자체가 보류 대상).
 */
public record MemberDetail(
        Long memberId,
        String name,
        Long teamId,
        String teamName,
        Long jobPositionId,
        String positionName,
        Authority role,
        boolean isAdmin,
        /** 역할 변경 카드가 현재 역할을 선택 상태로 그리려면 이름이 아니라 id 가 필요하다(§7-4). */
        Long roleId,
        String roleLabel,
        MemberStatus workStatus,
        String email,
        LocalDate joinedOn
) {
}
