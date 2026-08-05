package com.module06.backend.meeting.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * meeting 테이블 저장을 수행하는 Spring Data JPA 기술 저장소다.
 */
public interface SpringDataMeetingRepository extends JpaRepository<MeetingJpaEntity, Long> {

    /* 식별자와 회사가 모두 일치하는 회의를 조회해 타 회사 데이터 존재 여부를 숨긴다. */
    Optional<MeetingJpaEntity> findByIdAndCompanyId(Long id, Long companyId);

    /* E 타임라인용 프로젝트 회의를 시작 시각과 식별자 순서로 조회한다. */
    List<MeetingJpaEntity> findAllByCompanyIdAndProjectIdOrderByStartAtAscIdAsc(
            Long companyId,
            Long projectId
    );

    /* 배치 참석자 조회 전에 요청 회사에 속한 회의 식별자만 선별한다. */
    List<MeetingJpaEntity> findAllByIdInAndCompanyId(List<Long> ids, Long companyId);

    /* MEET-03 후보 회의를 회사·상태·종료 시각으로 필터링하고 시작 시각 순으로 조회한다. */
    List<MeetingJpaEntity> findAllByIdInAndCompanyIdAndStatusInAndEndAtGreaterThanEqualOrderByStartAtAscIdAsc(
            List<Long> ids,
            Long companyId,
            List<MeetingStatus> statuses,
            LocalDateTime endAt
    );
}
