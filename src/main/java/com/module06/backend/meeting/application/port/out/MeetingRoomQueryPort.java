package com.module06.backend.meeting.application.port.out;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/*
 * MEET-01이 회의실 도메인의 활성 회의실 정보를 조회하는 아웃바운드 포트다.
 *
 * 회의 애플리케이션은 meetingroom의 JPA 엔티티나 저장소 구현을 직접 참조하지 않는다.
 */
public interface MeetingRoomQueryPort {

    /* 요청 회사에 속한 활성 회의실을 조회한다. */
    Optional<MeetingRoomSnapshot> findActiveMeetingRoom(Long companyId, Long meetingRoomId);

    /* 예정 회의 카드에 필요한 회의실을 비활성 여부와 무관하게 회사 범위에서 일괄 조회한다. */
    List<MeetingRoomSnapshot> findMeetingRooms(Long companyId, List<Long> meetingRoomIds);

    /* 예약 검증과 응답 조립에 필요한 회의실 읽기 모델이다. */
    record MeetingRoomSnapshot(
            Long meetingRoomId,
            String name,
            String location,
            LocalTime availableFrom,
            LocalTime availableTo
    ) {
    }
}
