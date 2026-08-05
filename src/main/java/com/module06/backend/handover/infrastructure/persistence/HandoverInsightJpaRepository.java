package com.module06.backend.handover.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HandoverInsightJpaRepository extends JpaRepository<HandoverInsightJpaEntity, Long> {

    /*
     * 재확정(replace-all) 시 삭제를 삽입보다 먼저 DB에 반영해야 유니크 제약 위반을 피한다.
     * 파생 삭제는 flush 시점에 삽입 뒤로 밀리므로 벌크 삭제 + 자동 flush/clear로 순서를 강제한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HandoverInsightJpaEntity e where e.handoverId = :handoverId")
    void deleteByHandoverId(@Param("handoverId") Long handoverId);
}
