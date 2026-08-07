package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.Meeting;

/*
 * MEET-05 회의 정보 수정에 필요한 잠금 조회와 원자 저장 계약이다.
 *
 * 구현체는 같은 회의 수정 요청을 회의 행 잠금으로 직렬화하고 예약이 바뀌는 경우
 * meeting 행과 meeting_room_slot 행을 하나의 트랜잭션 결과로 반영해야 한다.
 */
public interface MeetingUpdateRepository {

    /* 회사 범위의 회의 행을 잠근 뒤 최신 참석자 명단을 포함한 애그리거트로 조회한다. */
    Optional<Meeting> findForUpdate(Long companyId, Long meetingId);

    /* 수정된 회의를 저장하고 필요하면 기존 예약 슬롯을 최종 예약 범위로 교체한다. */
    Meeting saveUpdate(Meeting meeting, boolean reservationChanged);
}
