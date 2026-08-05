package com.module06.backend.meeting.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingReservationSlotId;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingReservationSlotJpaEntity;

/*
 * meeting_room_slot 검증 조회를 수행하는 Spring Data JPA 기술 저장소다.
 *
 * 실제 예약 삽입은 기존 행을 갱신하지 않고 PK 충돌을 내야 하므로 영속성 어댑터가 persist를 직접 사용한다.
 */
public interface SpringDataMeetingReservationSlotRepository
        extends JpaRepository<MeetingReservationSlotJpaEntity, MeetingReservationSlotId> {

    /* 특정 회의가 점유한 슬롯을 시작 시각 순서로 조회한다. */
    List<MeetingReservationSlotJpaEntity> findAllByMeetingIdOrderBySlotStartAsc(Long meetingId);
}
