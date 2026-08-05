package com.module06.backend.meetingroom.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingReferenceEntity;

/*
 * meeting 테이블을 읽기 전용으로 조회하는 Spring Data JPA 저장소다.
 *
 * 예약 슬롯이 가리키는 회의의 제목을 채우고, 회사 조건을 함께 걸어 테넌트 스코프를 검증하는 용도로만 쓴다.
 */
public interface SpringDataMeetingReferenceRepository extends JpaRepository<MeetingReferenceEntity, Long> {

    /*
     * 회의 식별자 목록 중 요청 회사에 속한 회의만 조회한다.
     * 슬롯 수가 아니라 회의 수만큼의 식별자로 한 번만 조회하므로 슬롯마다 질의가 발생하지 않는다.
     *
     * @param ids 조회할 회의 식별자 목록
     * @param companyId 인증된 요청자의 회사 식별자
     * @return 요청 회사에 속한 회의 참조 엔티티 목록
     */
    List<MeetingReferenceEntity> findAllByIdInAndCompanyId(List<Long> ids, Long companyId);
}
