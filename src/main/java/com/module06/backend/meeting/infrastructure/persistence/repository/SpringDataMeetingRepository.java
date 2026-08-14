package com.module06.backend.meeting.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * meeting 테이블 저장을 수행하는 Spring Data JPA 기술 저장소다.
 */
public interface SpringDataMeetingRepository
        extends JpaRepository<MeetingJpaEntity, Long>, JpaSpecificationExecutor<MeetingJpaEntity> {

    /* 식별자와 회사가 모두 일치하는 회의를 조회해 타 회사 데이터 존재 여부를 숨긴다. */
    Optional<MeetingJpaEntity> findByIdAndCompanyId(Long id, Long companyId);

    /* MEET-09가 기존 참석자 명단을 읽기 전에 대상 회의 행을 쓰기 잠금으로 선점한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingJpaEntity> findLockedByIdAndCompanyId(Long id, Long companyId);

    /* E 타임라인용 프로젝트 회의를 시작 시각과 식별자 순서로 조회한다. */
    List<MeetingJpaEntity> findAllByCompanyIdAndProjectIdOrderByStartAtAscIdAsc(
            Long companyId,
            Long projectId
    );

    /* 프로젝트 목록용 집계를 위해 회사·프로젝트 목록에서 특정 상태가 아닌 프로젝트 ID만 조회한다. */
    List<ProjectIdProjection> findAllByCompanyIdAndProjectIdInAndStatusNot(
            Long companyId,
            List<Long> projectIds,
            MeetingStatus excludedStatus
    );

    /* meeting의 큰 표시·시간 컬럼을 메모리에 올리지 않고 집계 키 한 컬럼만 읽는 닫힌 프로젝션이다. */
    interface ProjectIdProjection {

        /* 집계할 회의의 프로젝트 식별자를 반환한다. */
        Long getProjectId();
    }

    /* MEET-10 후보 조회 — 요청 회사에서 사용자가 host인 특정 상태 회의를 최근 시작 순으로 읽는다. */
    List<MeetingJpaEntity> findAllByCompanyIdAndHostMemberIdAndStatusOrderByStartAtDescIdDesc(
            Long companyId,
            Long hostMemberId,
            MeetingStatus status
    );

    /*
     * MEET-15 후보 조회 — MEET-10과 같은 조건에 MEET-18 온라인 회의(startAt 없음) 제외를 더한다.
     * startAt 기준 필터·정렬을 그대로 쓰는 화면이라 companyId·hostMemberId·status만 공유하는
     * MEET-10용 메서드를 그대로 쓰면 안 되고, isOnline=false로 걸러야 startAt null 비교가 없어진다.
     */
    List<MeetingJpaEntity> findAllByCompanyIdAndHostMemberIdAndStatusAndIsOnlineFalseOrderByStartAtDescIdDesc(
            Long companyId,
            Long hostMemberId,
            MeetingStatus status
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

    /* 10분 전 알림 대상인 예약 회의를 반개구간 시간창과 안정적인 순서로 조회한다. */
    List<MeetingJpaEntity> findAllByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscIdAsc(
            MeetingStatus status,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );
}
