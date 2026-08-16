package com.module06.backend.meeting.application.port.in;

import java.time.LocalDateTime;

/*
 * 액션 분배가 확정된 회의를 D 도메인에 기록하는 인바운드 계약이다.
 *
 * capture는 확정 시각만 전달하고 meeting 테이블의 저장 방식과 목록 노출 정책은 D가 소유한다.
 */
@FunctionalInterface
public interface MeetingActionConfirmationPort {

    /* 회사 범위 회의에 최초 액션 분배 확정 시각을 기록한다. */
    void confirmActions(Long companyId, Long meetingId, LocalDateTime confirmedAt);
}
