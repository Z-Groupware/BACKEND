package com.module06.backend.meetingroom.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;

/*
 * 회의실 관리 명령이 미래 SCHEDULED 예약 시간을 조회하는 도메인 저장소 계약이다.
 *
 * 회의 엔티티 전체를 회의실 애플리케이션에 노출하지 않고 시간 충돌 판단에 필요한 값만 반환한다.
 */
public interface MeetingRoomReservationRepository {

    /* 특정 회사·회의실에서 기준 시각 이후 시작하는 미래 SCHEDULED 예약을 조회한다. */
    List<ScheduledMeetingReservation> findFutureScheduledReservations(
            Long companyId,
            Long meetingRoomId,
            LocalDateTime fromInclusive
    );
}
