package com.module06.backend.meetingroom.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/*
 * meeting_attendee 테이블을 읽기 전용으로 조회하는 Spring Data JPA 저장소다.
 *
 * 회의 제목 열람 권한 판단에 필요한 최소 정보만 조회하며, 참석자 명단 전체를 불러오지 않는다.
 */
public interface SpringDataMeetingAttendeeReferenceRepository
        extends JpaRepository<MeetingAttendeeReferenceEntity, MeetingAttendeeReferenceId> {

    /*
     * 주어진 회의 중 특정 구성원이 참석자로 등록된 회의의 식별자만 조회한다.
     * 엔티티가 아닌 식별자만 선택해 판단에 쓰지 않는 데이터를 영속성 컨텍스트에 올리지 않는다.
     *
     * @param memberId 인증된 요청자의 구성원 식별자
     * @param meetingIds 참석 여부를 확인할 회의 식별자 목록
     * @return 요청자가 참석자인 회의 식별자 목록
     */
    @Query("""
            SELECT attendee.meetingId
            FROM MeetingAttendeeReferenceEntity attendee
            WHERE attendee.memberId = :memberId
              AND attendee.meetingId IN :meetingIds
            """)
    List<Long> findAttendedMeetingIds(
            @Param("memberId") Long memberId,
            @Param("meetingIds") List<Long> meetingIds
    );
}
