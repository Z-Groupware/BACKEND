package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;

/* 액션 분배 확정 시각을 meeting 행에 원자적으로 기록하는 도메인 저장소 계약이다. */
public interface MeetingActionConfirmationRepository {

    /* 값이 없는 최초 요청에서만 확정 시각을 저장하고 반복 요청은 기존 시각을 유지한다. */
    void confirmActionsIfAbsent(Long companyId, Long meetingId, LocalDateTime confirmedAt);
}
