package com.module06.backend.identity.member.application.dto;

import java.time.LocalDate;

import com.module06.backend.identity.member.domain.model.MemberStatus;

/**
 * 오너 대시보드 "팀장 현황" 한 행.
 *
 * <p>휴직 기간은 "8월 1일~15일" 같은 완성된 문자열이 아니라 날짜 원자값으로 낸다 — 표시 포맷이
 * 바뀌어도 이 API 를 다시 건드리지 않는다. 휴직 중이 아니면 둘 다 null.
 */
public record TeamLeaderStatus(
        Long memberId,
        String name,
        String email,
        Long teamId,
        String teamName,
        MemberStatus status,
        LocalDate leaveStartDate,
        LocalDate leaveEndDate
) {
}
