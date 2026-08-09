package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.Meeting;

/* MEET-08의 회사 범위 회의 잠금 조회와 완료 상태 저장 계약이다. */
public interface MeetingCompletionRepository {

    /* 회사 범위 회의를 비관적으로 잠그고 최신 참석자 명단까지 복원한다. */
    Optional<Meeting> findForCompletion(Long companyId, Long meetingId);

    /* DONE 상태와 실제 종료 시각이 반영된 기존 회의를 저장한다. */
    Meeting saveCompleted(Meeting meeting);
}
