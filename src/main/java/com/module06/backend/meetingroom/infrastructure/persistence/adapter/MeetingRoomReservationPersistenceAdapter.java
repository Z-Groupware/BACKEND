package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomReservationRepository;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingReferenceEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingReferenceRepository;

/*
 * ROOM-04 미래 SCHEDULED 예약 조회 계약을 meeting 읽기 전용 엔티티로 구현하는 어댑터다.
 */
@Component
@RequiredArgsConstructor
public class MeetingRoomReservationPersistenceAdapter implements MeetingRoomReservationRepository {

    /* meeting 테이블을 회사·회의실·상태·시작 시각으로 조회하는 기술 저장소다. */
    private final SpringDataMeetingReferenceRepository springDataMeetingReferenceRepository;

    /* 기준 시각 이후 시작하는 해당 회사·회의실의 SCHEDULED 예약 시간을 반환한다. */
    @Override
    public List<ScheduledMeetingReservation> findFutureScheduledReservations(
            Long companyId,
            Long meetingRoomId,
            LocalDateTime fromInclusive
    ) {
        /* 상태 문자열을 저장 스키마 값과 동일하게 고정하고 필요한 시간 값만 도메인으로 변환한다. */
        return springDataMeetingReferenceRepository
                .findAllByCompanyIdAndMeetingRoomIdAndStatusAndStartAtGreaterThanEqualOrderByStartAtAsc(
                        companyId,
                        meetingRoomId,
                        "SCHEDULED",
                        fromInclusive
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    /* 읽기 전용 meeting 엔티티를 시간 충돌 판단용 도메인 값으로 변환한다. */
    private ScheduledMeetingReservation toDomain(MeetingReferenceEntity entity) {
        /* 회의 제목과 다른 식별자는 노출하지 않고 시작·종료 일시만 전달한다. */
        return new ScheduledMeetingReservation(entity.getStartAt(), entity.getEndAt());
    }
}
