package com.module06.backend.capture.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;

public interface SpringDataAnalysisLayerRepository extends JpaRepository<AnalysisLayerJpaEntity, Long> {

    Optional<AnalysisLayerJpaEntity> findByMeetingIdAndLayer(Long meetingId, String layer);

    List<AnalysisLayerJpaEntity> findByMeetingIdOrderByIdAsc(Long meetingId);

    /*
     * 기존 행을 RUNNING 으로 **한 문장에** 전이시킨다. 갱신된 행 수가 1 이면 이번 호출이 잡았다.
     *
     * 조회 → 검사 → 저장으로 나누면 두 실행이 같은 "RUNNING 아님"을 읽고 둘 다 RUNNING 으로
     * 저장한다. 그러면 같은 회의에 계층을 두 번 돌려 **토큰이 그대로 두 배**가 된다.
     * INSERT 경로는 UNIQUE(meeting_id, layer) 가 막지만 UPDATE 경로는 막을 게 없어서,
     * 조건을 WHERE 로 내려 DB 가 직렬화하게 한다.
     *
     * 이전 실행의 메타데이터를 함께 지운다(finishedAt · errorCode · errorMessage ·
     * modelName · promptVersion). 남겨 두면 이번 시도가 모델을 부르기도 전에 실패했을 때
     * 지난 실행의 모델·프롬프트 버전이 이번 것으로 읽힌다.
     *
     * ⚠ tokensIn/Out 은 **지우지 않는다.** 재시도로 태운 토큰도 실제로 나간 비용이라,
     *   0 으로 되돌리면 QLTY-03 이 실패한 시도의 비용을 잃는다(markDone·markFailed 가 더한다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisLayerJpaEntity e
               set e.status = :running,
                   e.attemptCount = e.attemptCount + 1,
                   e.startedAt = :now,
                   e.finishedAt = null,
                   e.errorCode = null,
                   e.errorMessage = null,
                   e.modelName = null,
                   e.promptVersion = null
             where e.meetingId = :meetingId
               and e.layer = :layer
               and e.status <> :running
            """)
    int tryTransitionToRunning(@Param("meetingId") Long meetingId,
                              @Param("layer") String layer,
                              @Param("running") LayerStatus running,
                              @Param("now") LocalDateTime now);
}
