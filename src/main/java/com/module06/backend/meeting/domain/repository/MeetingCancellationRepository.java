package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.Meeting;

/* MEET-06의 회사 범위 회의 잠금 조회와 상태·예약 슬롯 원자 변경 계약이다. */
public interface MeetingCancellationRepository {

    /* 회사 범위 취소 대상 회의를 비관적으로 잠그고 최신 참석자 명단까지 복원한다. */
    Optional<Meeting> findForCancellation(Long companyId, Long meetingId);

    /* CANCELED 상태와 취소 시각을 저장하고 현재 회의가 점유한 슬롯을 모두 해제한다. */
    Meeting saveCancellationAndReleaseSlots(Meeting meeting);
}
