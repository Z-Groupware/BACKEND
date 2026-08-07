package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAnalysisRunJpaEntity;

public interface SpringDataMeetingAnalysisRunRepository
        extends JpaRepository<MeetingAnalysisRunJpaEntity, Long> {

    /*
     * 실행 번호 행에 쓰기 잠금을 걸고 읽는다(SELECT ... FOR UPDATE).
     *
     * 발급(begin)과 확인(tryLock)이 **같은 행 잠금 위에서** 줄을 서야 순서가 성립한다.
     * 잠금 없이 읽으면 두 실행이 같은 값을 읽고 둘 다 "내가 최신"이라고 판단한다 —
     * 그러면 이 테이블은 아무것도 막지 못하고 컬럼만 하나 늘어난 것이 된다.
     *
     * ⚠ 반드시 트랜잭션 안에서 불러야 잠금이 유지된다(어댑터가 REQUIRES_NEW 로 감싼다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeetingAnalysisRunJpaEntity> findWithLockByMeetingId(Long meetingId);
}
