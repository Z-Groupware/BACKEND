package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;

public interface SpringDataAnalysisLayerRepository extends JpaRepository<AnalysisLayerJpaEntity, Long> {

    Optional<AnalysisLayerJpaEntity> findByMeetingIdAndLayer(Long meetingId, String layer);

    /*
     * 잠금 전이용 조회 — 행에 쓰기 잠금을 걸고 읽는다(SELECT ... FOR UPDATE).
     *
     * 잠금 없이 조회→검사→저장으로 나누면 두 실행이 같은 "RUNNING 아님"을 읽고 **둘 다 잠근
     * 것으로 판단한다.** 그러면 같은 회의에 계층을 두 번 돌려 토큰이 그대로 두 배가 된다.
     * INSERT 경로는 UNIQUE(meeting_id, layer) 가 막지만 UPDATE 경로는 막을 게 없어서,
     * 행 잠금으로 DB 가 직렬화하게 한다.
     *
     * 조건부 UPDATE(@Query) 로도 같은 보장을 얻지만 QUERY_002(신규 @Query 금지)에 걸린다.
     * 파생 메서드 + @Lock 이 규칙을 지키면서 같은 결과를 주고, 초기화 로직도 JPQL 문자열이
     * 아니라 엔티티 한 곳(restart)에 남아 읽기 쉽다.
     *
     * ⚠ 반드시 트랜잭션 안에서 불러야 잠금이 유지된다(어댑터가 REQUIRES_NEW 로 감싼다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AnalysisLayerJpaEntity> findWithLockByMeetingIdAndLayer(Long meetingId, String layer);

    List<AnalysisLayerJpaEntity> findByMeetingIdOrderByIdAsc(Long meetingId);
}
