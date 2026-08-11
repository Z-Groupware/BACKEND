package com.module06.backend.meeting.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;

/*
 * 회의 알림 대상 어댑터가 예약 회의 시간창과 회사별 회의실 배치 조회를 지키는지 검증한다.
 */
@DisplayName("회의 10분 전 알림 대상 조회 어댑터")
class MeetingReminderQueryAdapterTest {

    /* 예약 회의만 조회하고 서로 다른 회사의 회의실 이름을 올바르게 조립하는지 검증한다. */
    @Test
    @DisplayName("SCHEDULED 시간창 조회 후 회사별 회의실 이름을 배치 조립한다")
    void findsScheduledMeetingsAndMapsRoomsByCompany() {
        /* 알림 시간창 경계와 서로 다른 회사의 회의 두 건을 준비한다. */
        LocalDateTime from = LocalDateTime.of(2026, 8, 10, 13, 30);
        LocalDateTime to = LocalDateTime.of(2026, 8, 10, 13, 31);
        MeetingJpaEntity companyOneMeeting = meeting(501L, 1L, 21L, "스프린트 회고", from);
        MeetingJpaEntity companyTwoMeeting = meeting(502L, 2L, 21L, "경영 회의", from.plusSeconds(30));

        /* 기술 저장소와 회의실 Port의 호출·반환을 제어하는 대역을 만든다. */
        SpringDataMeetingRepository repository = mock(SpringDataMeetingRepository.class);
        MeetingRoomQueryPort meetingRoomQueryPort = mock(MeetingRoomQueryPort.class);
        when(repository.findAllByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscIdAsc(
                MeetingStatus.SCHEDULED,
                from,
                to
        )).thenReturn(List.of(companyOneMeeting, companyTwoMeeting));
        when(meetingRoomQueryPort.findMeetingRooms(1L, List.of(21L)))
                .thenReturn(List.of(room(21L, "대회의실")));
        when(meetingRoomQueryPort.findMeetingRooms(2L, List.of(21L)))
                .thenReturn(List.of(room(21L, "본사 회의실")));

        /* 알림 Port 구현에 동일한 반개구간 조회를 요청한다. */
        MeetingReminderQueryAdapter adapter = new MeetingReminderQueryAdapter(repository, meetingRoomQueryPort);
        var result = adapter.findScheduledMeetingsStartingBetween(from, to);

        /* 회사가 달라도 같은 회의실 식별자의 이름이 서로 섞이지 않아야 한다. */
        assertThat(result).hasSize(2);
        assertThat(result.get(0).companyId()).isEqualTo(1L);
        assertThat(result.get(0).meetingRoomName()).isEqualTo("대회의실");
        assertThat(result.get(1).companyId()).isEqualTo(2L);
        assertThat(result.get(1).meetingRoomName()).isEqualTo("본사 회의실");

        /* 저장소에는 상태와 시간 경계가 그대로 전달돼야 한다. */
        verify(repository).findAllByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscIdAsc(
                MeetingStatus.SCHEDULED,
                from,
                to
        );
    }

    /* 조회 대상이 없을 때 회의실 Port를 호출하지 않고 빈 결과를 반환하는지 검증한다. */
    @Test
    @DisplayName("대상 회의가 없으면 회의실을 조회하지 않는다")
    void skipsRoomLookupWhenNoMeetings() {
        /* 빈 회의 조회 결과를 반환하는 저장소 대역을 준비한다. */
        LocalDateTime from = LocalDateTime.of(2026, 8, 10, 13, 30);
        LocalDateTime to = from.plusMinutes(1);
        SpringDataMeetingRepository repository = mock(SpringDataMeetingRepository.class);
        MeetingRoomQueryPort meetingRoomQueryPort = mock(MeetingRoomQueryPort.class);
        when(repository.findAllByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscIdAsc(
                MeetingStatus.SCHEDULED,
                from,
                to
        )).thenReturn(List.of());

        /* 빈 조회 결과를 그대로 반환받는다. */
        MeetingReminderQueryAdapter adapter = new MeetingReminderQueryAdapter(repository, meetingRoomQueryPort);
        var result = adapter.findScheduledMeetingsStartingBetween(from, to);

        /* 결과가 비어 있어야 하며 회의실 대역에는 어떤 호출도 없어야 한다. */
        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(meetingRoomQueryPort);
    }

    /* 공개 getter를 가진 JPA 엔티티 대역으로 어댑터 매핑 입력을 준비한다. */
    private MeetingJpaEntity meeting(
            Long meetingId,
            Long companyId,
            Long meetingRoomId,
            String title,
            LocalDateTime startAt
    ) {
        /* 영속성 생성 세부사항과 무관하게 필요한 컬럼 getter만 지정한다. */
        MeetingJpaEntity meeting = mock(MeetingJpaEntity.class);
        when(meeting.getId()).thenReturn(meetingId);
        when(meeting.getCompanyId()).thenReturn(companyId);
        when(meeting.getMeetingRoomId()).thenReturn(meetingRoomId);
        when(meeting.getTitle()).thenReturn(title);
        when(meeting.getStartAt()).thenReturn(startAt);
        when(meeting.getEndAt()).thenReturn(startAt.plusMinutes(30));
        return meeting;
    }

    /* 회사별 회의실 표시값을 가진 Port 스냅숏을 만든다. */
    private MeetingRoomSnapshot room(Long meetingRoomId, String name) {
        /* 위치와 운영 시간은 이번 알림 payload에서 사용하지 않으므로 최소 유효값만 넣는다. */
        return new MeetingRoomSnapshot(
                meetingRoomId,
                name,
                "3층",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
    }
}
