package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomReservationRepository;

/*
 * ROOM-04 운영 시간 축소 검증에 사용하는 미래 SCHEDULED 예약 파생 조회를 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("ROOM-04 미래 예약 영속성 어댑터")
class MeetingRoomReservationPersistenceAdapterTest {

    /* 완전한 meeting 행을 준비하고 초기화하는 회의 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* application 계층이 사용하는 미래 예약 시간 조회 계약이다. */
    @Autowired
    private MeetingRoomReservationRepository meetingRoomReservationRepository;

    /* 각 테스트가 독립적인 meeting 데이터로 실행되도록 테이블을 초기화한다. */
    @BeforeEach
    void clearMeetings() {
        /* 이전 테스트에서 저장한 회의를 모두 삭제한다. */
        springDataMeetingRepository.deleteAll();
    }

    /* 회사·회의실·상태·현재 시각 조건을 모두 만족하는 예약만 반환하는지 검증한다. */
    @Test
    @DisplayName("같은 회사와 회의실의 미래 SCHEDULED 예약만 조회한다")
    void findsOnlyFutureScheduledReservationsInRequestedScope() {
        /* 조회 대상인 미래 SCHEDULED 예약을 저장한다. */
        springDataMeetingRepository.save(meeting(
                10L,
                2L,
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 7, 16, 30),
                LocalDateTime.of(2026, 8, 7, 17, 30)
        ));

        /* 과거 예약과 미래지만 DONE 상태인 회의를 저장한다. */
        springDataMeetingRepository.save(meeting(
                10L,
                2L,
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 5, 10, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0)
        ));
        springDataMeetingRepository.save(meeting(
                10L,
                2L,
                MeetingStatus.DONE,
                LocalDateTime.of(2026, 8, 7, 14, 0),
                LocalDateTime.of(2026, 8, 7, 15, 0)
        ));

        /* 다른 회사 또는 다른 회의실의 미래 SCHEDULED 예약도 저장한다. */
        springDataMeetingRepository.save(meeting(
                20L,
                2L,
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 7, 12, 0),
                LocalDateTime.of(2026, 8, 7, 13, 0)
        ));
        springDataMeetingRepository.save(meeting(
                10L,
                3L,
                MeetingStatus.SCHEDULED,
                LocalDateTime.of(2026, 8, 7, 11, 0),
                LocalDateTime.of(2026, 8, 7, 12, 0)
        ));

        /* 회사 10의 2번 회의실에서 8월 6일 오전 9시 이후 시작하는 예약을 조회한다. */
        List<ScheduledMeetingReservation> result = meetingRoomReservationRepository
                .findFutureScheduledReservations(
                        10L,
                        2L,
                        LocalDateTime.of(2026, 8, 6, 9, 0)
                );

        /* 조건을 모두 만족하는 한 건의 시작·종료 일시만 반환돼야 한다. */
        assertThat(result).containsExactly(new ScheduledMeetingReservation(
                LocalDateTime.of(2026, 8, 7, 16, 30),
                LocalDateTime.of(2026, 8, 7, 17, 30)
        ));
    }

    /* 테스트 조건에 맞는 완전한 회의 영속성 엔티티를 만든다. */
    private MeetingJpaEntity meeting(
            Long companyId,
            Long meetingRoomId,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        /* 지정한 상태와 시간 외의 필드는 meeting 테이블의 정상값으로 채운다. */
        Meeting meeting = Meeting.reconstitute(
                null,
                companyId,
                12L,
                100L,
                meetingRoomId,
                3L,
                "예약 검증 회의",
                status,
                startAt,
                endAt,
                false,
                null,
                List.of(3L),
                null,
                status == MeetingStatus.DONE ? endAt : null,
                LocalDateTime.of(2026, 8, 5, 9, 0),
                LocalDateTime.of(2026, 8, 5, 9, 0)
        );

        /* 회의 도메인을 운영 저장 경로와 같은 JPA 엔티티로 변환한다. */
        return MeetingJpaEntity.from(meeting);
    }
}
