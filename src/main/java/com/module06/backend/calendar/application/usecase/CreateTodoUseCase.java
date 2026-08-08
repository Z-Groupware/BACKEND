package com.module06.backend.calendar.application.usecase;

import com.module06.backend.calendar.application.command.CreateTodoCommand;
import com.module06.backend.calendar.domain.model.PersonalTodo;

/* comment.
    개인 Todo 생성. 항상 미완료로 시작한다.

    연결된 클래스
    - PersonalTodoRepository : 저장
    - TodoResponse           : 출력 DTO (presentation)
    - TodoController         : 호출자 (presentation)
*/
public interface CreateTodoUseCase {

    PersonalTodo create(CreateTodoCommand command);
}
