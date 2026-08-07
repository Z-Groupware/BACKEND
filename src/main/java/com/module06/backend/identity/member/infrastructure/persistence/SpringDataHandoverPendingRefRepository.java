package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 대기 중(FINALIZED·REJECTED 아님)인 handover 행을 작성자 id 배치로 읽는다. 쓰기는 하지 않는다. */
interface SpringDataHandoverPendingRefRepository extends JpaRepository<HandoverPendingRefEntity, Long> {

    List<HandoverPendingRefEntity> findByWriterMemberIdInAndStatusNotIn(
            List<Long> writerMemberIds, List<String> excludedStatuses);
}
