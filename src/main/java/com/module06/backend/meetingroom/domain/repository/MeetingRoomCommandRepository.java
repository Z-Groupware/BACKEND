package com.module06.backend.meetingroom.domain.repository;

import java.util.Optional;

import com.module06.backend.meetingroom.domain.model.MeetingRoom;

/*
 * 회의실 등록·수정·비활성화 명령에 필요한 영속성 계약이다.
 *
 * 목록과 현황 조회 계약을 분리해 쓰기 유스케이스가 조회 전용 메서드에 의존하지 않게 한다.
 */
public interface MeetingRoomCommandRepository {

    /* 회사 안에 같은 이름을 가진 활성 회의실이 존재하는지 확인한다. */
    boolean existsActiveByCompanyIdAndName(Long companyId, String name);

    /* 수정 트랜잭션 동안 회사의 활성 회의실 행을 잠가 예약 생성과 변경의 경쟁을 직렬화한다. */
    Optional<MeetingRoom> findActiveByIdForUpdate(Long companyId, Long meetingRoomId);

    /* 현재 수정 대상 이외에 같은 이름을 가진 활성 회의실이 존재하는지 확인한다. */
    boolean existsActiveByCompanyIdAndNameExcludingId(Long companyId, String name, Long excludedMeetingRoomId);

    /* 신규 또는 변경된 회의실을 저장하고 데이터베이스 식별자가 반영된 도메인 객체를 반환한다. */
    MeetingRoom save(MeetingRoom meetingRoom);
}
