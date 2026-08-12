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

    /*
     * TENANT_001 예외: handover 테이블에는 company_id 컬럼이 없어(V7.0) 메서드 이름으로 회사를
     * 좁힐 방법이 없다. 대신 writerMemberIds 가 이미 회사로 좁혀진 값이다 — 호출자
     * (MemberDirectoryQueryAdapter)가 findByCompanyIdAndDeletedAtIsNull 로 얻은 구성원의 id 만
     * 넣는다. 구성원은 회사 하나에만 속하므로 그 id 로 읽은 handover 행도 그 회사 것뿐이다.
     *
     * 다른 회사 행이 새려면 호출자가 회사 밖 memberId 를 넘겨야 하는데, 이 인터페이스가
     * package-private 이라 그 실수는 이 패키지 안에서만 가능하다.
     */
    // nosemgrep: tenant-derived-query-without-company-scope
    List<HandoverRefEntity> findByWriterMemberIdInAndStatusNot(List<Long> writerMemberIds, String excludedStatus);
}
