package com.module06.backend.meetingroom.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.meetingroom.domain.model.MeetingRoom;

/*
 * 회의실 영속성 기능을 추상화한 도메인 저장소 계약이다.
 *
 * domain 계층은 JPA와 데이터베이스 구현을 알지 않으며,
 * infrastructure 계층의 PersistenceAdapter가 이 계약을 구현한다.
 */
public interface MeetingRoomRepository {

    /*
     * 특정 회사에 속하면서 비활성화되지 않은 회의실을 조회한다.
     * 구현체는 회의실 이름 오름차순과 회의실 식별자 오름차순으로 결과를 정렬해야 한다.
     *
     * @param companyId 조회할 회사 식별자
     * @return 회사에 속한 활성 회의실 목록, 조회 결과가 없으면 빈 목록
     */
    List<MeetingRoom> findAllActiveByCompanyId(Long companyId);

    /*
     * 특정 회사에 속한 활성 회의실 하나를 조회한다.
     * 회사 조건을 조회 자체에 포함해, 다른 회사의 회의실은 권한 오류가 아니라 결과 없음으로 다룬다.
     *
     * @param companyId 조회할 회사 식별자
     * @param meetingRoomId 조회할 회의실 식별자
     * @return 조건을 만족하는 활성 회의실, 없으면 빈 Optional
     */
    Optional<MeetingRoom> findActiveById(Long companyId, Long meetingRoomId);

    /* 기존 예정 회의가 참조하는 회의실을 비활성 여부와 무관하게 회사 범위에서 일괄 조회한다. */
    List<MeetingRoom> findAllByIds(Long companyId, List<Long> meetingRoomIds);
}
