package com.module06.backend.calendar.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.module06.backend.calendar.domain.model.PersonalTodo;

/* comment.
    personal_todo 저장소 계약. action 저장소와 같은 패턴 — 착수한 슬라이스에 필요한
    메서드만 채운다.
*/
public interface PersonalTodoRepository {

    PersonalTodo save(PersonalTodo todo);

    Optional<PersonalTodo> findById(Long id);

    // 캘린더 월별 조회 — 호출자 본인 소유분만, [date, endDate] 구간이 [periodStart, periodEnd]와
    // 겹치는 Todo를 전부 반환한다(시작일이 이전 달이어도 종료일이 조회 월에 걸치면 포함).
    List<PersonalTodo> findAllByMemberIdOverlappingPeriod(Long memberId, LocalDate periodStart, LocalDate periodEnd);
}
