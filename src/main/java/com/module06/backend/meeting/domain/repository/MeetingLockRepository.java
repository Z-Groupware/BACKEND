package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;

/*
 * 동일 회의의 상태 변경을 직렬화하기 위한 회의 잠금 저장소 계약이다.
 *
 * 일반 조회에는 잠금을 사용하지 않고, 기존 상태를 기준으로 차이를 계산하는 명령에서만
 * 회의 행을 선점해 참석자 명단과 이벤트가 하나의 일관된 기준 상태를 사용하도록 한다.
 */
public interface MeetingLockRepository {

    /* 회사 범위의 회의 행을 비관적으로 잠근 뒤 최신 참석자 명단과 함께 조회한다. */
    Optional<MeetingSnapshot> findMeetingForUpdate(Long companyId, Long meetingId);
}
