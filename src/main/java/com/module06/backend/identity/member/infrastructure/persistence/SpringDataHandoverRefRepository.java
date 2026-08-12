package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * handover 행을 작성자 id 배치로 읽는다. 쓰기는 하지 않는다.
 *
 * <p>반려(REJECTED)만 미리 걸러 한 번에 읽고, "대기 유형"과 "휴직 기간" 으로 가르는 일은 어댑터가
 * 자바에서 한다 — 두 용도로 쿼리를 두 번 날리면 같은 행을 두 번 읽게 된다.
 */
interface SpringDataHandoverRefRepository extends JpaRepository<HandoverRefEntity, Long> {

    List<HandoverRefEntity> findByWriterMemberIdInAndStatusNot(List<Long> writerMemberIds, String excludedStatus);
}
