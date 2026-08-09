package com.module06.backend.meetingroom.infrastructure.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;

/*
 * meeting의 회의실 조회 Port를 meetingroom 도메인 계약으로 구현하는 D도메인 내부 어댑터다.
 *
 * 회의 애플리케이션에는 회의실 도메인 모델을 노출하지 않고 필요한 읽기 값만 스냅숏으로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingRoomQueryAdapter implements MeetingRoomQueryPort {

    /* 회사와 활성 상태 조건을 포함하는 회의실 도메인 저장소 계약이다. */
    private final MeetingRoomRepository meetingRoomRepository;

    /* MEET-01 예약과 ROOM-04 운영 시간 변경을 같은 회의실 행 잠금으로 직렬화하는 명령 저장소다. */
    private final MeetingRoomCommandRepository meetingRoomCommandRepository;

    /* 요청 회사에 속한 활성 회의실을 MEET-01 읽기 모델로 변환한다. */
    @Override
    public Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId) {
        /* 타 회사 또는 비활성 회의실은 빈 결과가 되어 상위 계층에서 MR-001로 처리된다. */
        return meetingRoomCommandRepository.findActiveByIdForUpdate(companyId, meetingRoomId)
                .map(this::toSnapshot);
    }

    /* 예정 회의 목록에 필요한 회의실 표시 정보를 회사 범위에서 한 번에 조회한다. */
    @Override
    public List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds) {
        /* 회의실 활성 여부와 무관한 배치 도메인 조회 결과를 Port 스냅숏으로 변환한다. */
        return meetingRoomRepository.findAllByIds(companyId, meetingRoomIds)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    /* 회의실 도메인 모델을 meeting Port가 정의한 최소 스냅숏으로 변환한다. */
    private MeetingRoomSnapshot toSnapshot(MeetingRoom meetingRoom) {
        /* 예약 검증과 생성 응답에 필요한 값만 경계 밖으로 전달한다. */
        return new MeetingRoomSnapshot(
                meetingRoom.getId(),
                meetingRoom.getName(),
                meetingRoom.getLocation(),
                meetingRoom.getCapacity(),
                meetingRoom.getAvailableFrom(),
                meetingRoom.getAvailableTo()
        );
    }
}
