package com.module06.backend.calendar.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPersonalTodoRepository extends JpaRepository<PersonalTodoJpaEntity, Long> {

    // 파생쿼리로 overlap 조건을 표현한다(Semgrep QUERY_002가 신규 @Query를 막음).
    // 이름 순서(date → endDate) 그대로 인자를 넘겨야 한다 — periodEnd가 먼저, periodStart가 나중.
    List<PersonalTodoJpaEntity> findAllByMemberIdAndDateLessThanEqualAndEndDateGreaterThanEqual(
            Long memberId, LocalDate periodEnd, LocalDate periodStart);
}
