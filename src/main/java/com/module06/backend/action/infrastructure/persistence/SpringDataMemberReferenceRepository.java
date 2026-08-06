package com.module06.backend.action.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    MemberReferenceEntity 조회 전용. 수동 생성(FR-AC-01 예외 경로)의 assigneeMemberId가
    같은 회사 소속인지 검증하는 데 쓴다.
*/
public interface SpringDataMemberReferenceRepository extends JpaRepository<MemberReferenceEntity, Long> {

    boolean existsByIdAndCompanyId(Long id, Long companyId);
}
