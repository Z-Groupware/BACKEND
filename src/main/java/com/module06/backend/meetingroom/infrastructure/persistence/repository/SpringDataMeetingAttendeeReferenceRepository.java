package com.module06.backend.meetingroom.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingAttendeeReferenceEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingAttendeeReferenceId;

/*
 * meeting_attendee 테이블을 읽기 전용으로 조회하는 Spring Data JPA 저장소다.
 *
 * 회의 제목 열람 권한 판단에 필요한 최소 정보만 조회하며, 참석자 명단 전체를 불러오지 않는다.
 */
public interface SpringDataMeetingAttendeeReferenceRepository
        extends JpaRepository<MeetingAttendeeReferenceEntity, MeetingAttendeeReferenceId> {

    /*
     * 주어진 회의 중 특정 구성원이 참석자로 등록된 회의의 식별자만 조회한다.
     * QUERY_002 규칙에 따라 @Query 없이 파생 쿼리 메서드로 조건을 표현한다.
     * 참조 엔티티에는 복합 식별자 두 값만 매핑되어 있어 필요한 최소 데이터만 조회한다.
     *
     * @param memberId 인증된 요청자의 구성원 식별자
     * @param meetingIds 참석 여부를 확인할 회의 식별자 목록
     * @return 요청자가 참석자로 등록된 회의 참조 엔티티 목록
     */
    List<MeetingAttendeeReferenceEntity> findAllByMemberIdAndMeetingIdIn(
            Long memberId,
            List<Long> meetingIds
    );
}
