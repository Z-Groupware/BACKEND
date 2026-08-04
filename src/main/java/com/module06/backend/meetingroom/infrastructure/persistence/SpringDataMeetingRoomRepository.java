package com.module06.backend.meetingroom.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/*
 * meeting_room 테이블 조회를 수행하는 Spring Data JPA 저장소다.
 *
 * infrastructure 계층 내부에서만 기술 저장소로 사용하며,
 * application 계층은 MeetingRoomRepository 도메인 계약을 통해서만 데이터에 접근한다.
 */
public interface SpringDataMeetingRoomRepository extends JpaRepository<MeetingRoomJpaEntity, Long> {

    /*
     * 회사 식별자가 일치하고 삭제 시각이 없는 회의실을 이름과 식별자 오름차순으로 조회한다.
     * 이름이 같은 회의실도 식별자 정렬을 적용해 항상 동일한 응답 순서를 보장한다.
     *
     * @param companyId 조회할 회사 식별자
     * @return 정렬된 활성 회의실 영속성 엔티티 목록
     */
    List<MeetingRoomJpaEntity> findAllByCompanyIdAndDeletedAtIsNullOrderByNameAscIdAsc(Long companyId);
}
