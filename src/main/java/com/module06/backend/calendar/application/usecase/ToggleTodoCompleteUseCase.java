package com.module06.backend.calendar.application.usecase;

import com.module06.backend.calendar.domain.model.PersonalTodo;

/* comment.
    개인 Todo 완료 체크박스 토글 — 완료면 취소로, 미완료면 완료로(Figma 확인,
    2026-08-08). 본인 소유분만 가능하다.
*/
public interface ToggleTodoCompleteUseCase {

    PersonalTodo toggleComplete(Long companyId, Long memberId, Long todoId);
}
