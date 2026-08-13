package com.module06.backend.identity.member.presentation.api.dto.response;

import java.time.LocalDate;

import com.module06.backend.identity.member.application.dto.TeamLeaderStatus;

/**
 * 오너 대시보드 "팀장 현황" 한 행.
 *
 * <p>상태 이름은 {@code workStatus}(구성원 목록·상세의 이름) 가 아니라 {@code status} 로 낸다 —
 * 이 화면 전용 응답이고 FE 표와 이름을 맞췄다. 값은 같은 enum 이다(ACTIVE·VACATION·WAITING·RESIGNED).
 *
 * <p>휴직 기간은 ISO 날짜(예: 2026-08-01) 원자값이다. 표시 포맷은 FE 몫 — 포맷이 바뀌어도
 * 이 API 를 다시 건드리지 않는다. 휴직 중이 아니면 둘 다 null.
 */
public record TeamLeaderStatusResponse(
        Long memberId,
        String name,
        String email,
        Long teamId,
        String teamName,
        String status,
        LocalDate leaveStartDate,
        LocalDate leaveEndDate
) {

    public static TeamLeaderStatusResponse from(TeamLeaderStatus leader) {
        return new TeamLeaderStatusResponse(
                leader.memberId(), leader.name(), leader.email(), leader.teamId(), leader.teamName(),
                leader.status().name(), leader.leaveStartDate(), leader.leaveEndDate());
    }
}
