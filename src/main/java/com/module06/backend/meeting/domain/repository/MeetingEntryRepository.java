package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.Meeting;

/*
 * MEET-07의 회의 행 잠금 조회와 상태 저장을 제공하는 도메인 저장소 계약이다.
 */
public interface MeetingEntryRepository {

    /* 회사 범위의 회의를 비관적으로 잠그고 최신 참석자 명단을 포함한 애그리거트로 조회한다. */
    Optional<Meeting> findForEntry(Long companyId, Long meetingId);

    /* 최초 입장으로 변경된 회의 상태를 저장하고 영속성 값이 반영된 애그리거트를 반환한다. */
    Meeting saveState(Meeting meeting);
}
