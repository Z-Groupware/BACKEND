package com.module06.backend.meeting.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.notification.application.port.out.MeetingReminderQueryPort;

/*
 * notification의 회의 알림 대상 조회 계약을 meeting·meetingroom 데이터로 구현하는 D도메인 어댑터다.
 */
@Component
@RequiredArgsConstructor
public class MeetingReminderQueryAdapter implements MeetingReminderQueryPort {

    /* 예약 상태와 시작 시각으로 회의 후보를 조회하는 기술 저장소다. */
    private final SpringDataMeetingRepository springDataMeetingRepository;

    /* 대상 회의들의 최종 예약 참석자를 한 번에 조회하는 기술 저장소다. */
    private final SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 알림 payload의 회의실 이름을 회사별 배치 조회하는 D도메인 내부 Port다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 정확한 1분 시간창에 속한 SCHEDULED 회의를 알림 읽기 모델로 변환한다. */
    @Override
    public List<MeetingReminderTarget> findScheduledMeetingsStartingBetween(
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        /* 취소·진행·종료 회의를 제외하고 아직 예약 상태인 회의만 데이터베이스에서 조회한다. */
        List<MeetingJpaEntity> meetings = springDataMeetingRepository
                .findAllByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscIdAsc(
                        MeetingStatus.SCHEDULED,
                        fromInclusive,
                        toExclusive
                );

        /* 대상이 없으면 회의실 저장소를 불필요하게 조회하지 않는다. */
        if (meetings.isEmpty()) {
            return List.of();
        }

        /* 테넌트별 회의실 배치 조회 결과를 회사와 회의실 식별자의 복합 키로 색인한다. */
        Map<MeetingRoomKey, String> meetingRoomNames = indexMeetingRoomNamesByCompany(meetings);

        /* 회의별 반복 조회를 피하도록 모든 대상 회의의 참석자 명단을 한 번에 색인한다. */
        Map<Long, List<Long>> attendeeMemberIdsByMeeting = indexAttendeeMemberIdsByMeeting(meetings);

        /* 저장소 정렬을 보존하며 알림 도메인에 필요한 값만 경계 밖으로 전달한다. */
        return meetings.stream()
                .map(meeting -> new MeetingReminderTarget(
                        meeting.getCompanyId(),
                        meeting.getId(),
                        meeting.getTitle(),
                        meeting.getStartAt(),
                        meeting.getEndAt(),
                        meeting.getMeetingRoomId(),
                        meetingRoomNames.get(new MeetingRoomKey(
                                meeting.getCompanyId(),
                                meeting.getMeetingRoomId()
                        )),
                        attendeeMemberIdsByMeeting.getOrDefault(meeting.getId(), List.of())
                ))
                .toList();
    }

    /* 대상 회의 식별자를 한 번에 조회하고 회의별 최종 참석자 목록으로 묶는다. */
    private Map<Long, List<Long>> indexAttendeeMemberIdsByMeeting(List<MeetingJpaEntity> meetings) {
        /* 동일 회의가 중복 후보로 들어와도 IN 조건에는 한 번만 전달한다. */
        List<Long> meetingIds = meetings.stream()
                .map(MeetingJpaEntity::getId)
                .distinct()
                .toList();

        /* 저장소 정렬을 유지한 채 회의별 회원 식별자 목록을 만든다. */
        return springDataMeetingAttendeeRepository
                .findAllByMeetingIdInOrderByMeetingIdAscMemberIdAsc(meetingIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MeetingAttendeeJpaEntity::getMeetingId,
                        LinkedHashMap::new,
                        Collectors.mapping(MeetingAttendeeJpaEntity::getMemberId, Collectors.toList())
                ));
    }

    /* 같은 회사 회의실은 한 번에 조회해 회의별 반복 조회와 테넌트 혼합을 방지한다. */
    private Map<MeetingRoomKey, String> indexMeetingRoomNamesByCompany(List<MeetingJpaEntity> meetings) {
        /* 회의를 회사별로 묶되 원래 조회 순서를 유지한다. */
        Map<Long, List<MeetingJpaEntity>> meetingsByCompany = meetings.stream()
                .collect(Collectors.groupingBy(
                        MeetingJpaEntity::getCompanyId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        /* 회사별 회의실 조회 결과를 하나의 복합 키 색인으로 합친다. */
        Map<MeetingRoomKey, String> meetingRoomNames = new LinkedHashMap<>();
        meetingsByCompany.forEach((companyId, companyMeetings) -> {
            /* 중복 회의실 식별자를 제거해 Port에 전달하는 IN 조건 크기를 줄인다. */
            List<Long> meetingRoomIds = companyMeetings.stream()
                    .map(MeetingJpaEntity::getMeetingRoomId)
                    .distinct()
                    .toList();

            /* 비활성화된 회의실도 기존 예약의 표시 이력을 위해 조회 대상에 포함한다. */
            meetingRoomQueryPort.findMeetingRooms(companyId, meetingRoomIds)
                    .forEach(room -> meetingRoomNames.put(
                            new MeetingRoomKey(companyId, room.meetingRoomId()),
                            room.name()
                    ));
        });

        /* 누락된 회의실은 이름만 null로 유지하고 회의 알림 자체는 계속 보낼 수 있게 한다. */
        return meetingRoomNames;
    }

    /* 서로 다른 회사의 같은 회의실 식별자가 섞이지 않게 하는 내부 복합 키다. */
    private record MeetingRoomKey(Long companyId, Long meetingRoomId) {
    }
}
