package com.module06.backend.meetingroom.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingRoomJpaEntity;

/*
 * meeting_room 테이블 조회를 수행하는 Spring Data JPA 저장소다.
 *
 * infrastructure 계층 내부에서만 기술 저장소로 사용하며,
 * application 계층은 MeetingRoomRepository 도메인 계약을 통해서만 데이터에 접근한다.
 */
public interface SpringDataMeetingRoomRepository extends JpaRepository<MeetingRoomJpaEntity, Long> {

    /* 같은 회사의 활성 회의실 중 정규화된 이름이 같은 행이 존재하는지 확인한다. */
    boolean existsByCompanyIdAndNameAndDeletedAtIsNull(Long companyId, String name);

    /* 현재 회의실을 제외하고 같은 회사의 활성 이름이 존재하는지 확인한다. */
    boolean existsByCompanyIdAndNameAndDeletedAtIsNullAndIdNot(Long companyId, String name, Long id);

    /* 수정·예약 트랜잭션 동안 활성 회의실 행을 쓰기 잠금으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingRoomJpaEntity> findForUpdateByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    /*
     * 회사 식별자가 일치하고 삭제 시각이 없는 회의실을 이름과 식별자 오름차순으로 조회한다.
     * 이름이 같은 회의실도 식별자 정렬을 적용해 항상 동일한 응답 순서를 보장한다.
     *
     * @param companyId 조회할 회사 식별자
     * @return 정렬된 활성 회의실 영속성 엔티티 목록
     */
    List<MeetingRoomJpaEntity> findAllByCompanyIdAndDeletedAtIsNullOrderByNameAscIdAsc(Long companyId);

    /*
     * 식별자와 회사가 모두 일치하고 삭제 시각이 없는 회의실을 조회한다.
     * 회사 조건을 쿼리에 함께 넣어 조회 단계에서 다른 회사의 회의실이 걸러지게 한다.
     *
     * @param id 조회할 회의실 식별자
     * @param companyId 조회할 회사 식별자
     * @return 조건을 만족하는 활성 회의실 영속성 엔티티, 없으면 빈 Optional
     */
    Optional<MeetingRoomJpaEntity> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    /* 예정 회의가 참조하는 회의실을 비활성 여부와 무관하게 회사 범위에서 일괄 조회한다. */
    List<MeetingRoomJpaEntity> findAllByCompanyIdAndIdInOrderByIdAsc(Long companyId, List<Long> ids);
}
