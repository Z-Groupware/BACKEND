package com.module06.backend.calendar.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPersonalTodoRepository extends JpaRepository<PersonalTodoJpaEntity, Long> {

    // 파생쿼리로 overlap 조건을 표현한다(Semgrep QUERY_002가 신규 @Query를 막음).
    // 이름 순서(date → endDate) 그대로 인자를 넘겨야 한다 — periodEnd가 먼저, periodStart가 나중.
    //
    // memberId로 이미 회사가 좁혀진다 — TENANT_001 예외. member_id는 전사 유일 PK라 다른
    // 회사 소속 memberId로는 애초에 이 회사의 personal_todo 행에 도달할 수 없다(기존
    // ActionRepository.findAllByAssigneeMemberId와 동일한 근거).
    // nosemgrep: tenant-derived-query-without-company-scope
    List<PersonalTodoJpaEntity> findAllByMemberIdAndDateLessThanEqualAndEndDateGreaterThanEqual(
            Long memberId, LocalDate periodEnd, LocalDate periodStart);
}
