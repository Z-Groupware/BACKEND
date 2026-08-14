package com.module06.backend.calendar.application.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.calendar.application.command.CreateTodoCommand;
import com.module06.backend.calendar.application.usecase.CreateTodoUseCase;
import com.module06.backend.calendar.application.usecase.ToggleTodoCompleteUseCase;
import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.domain.repository.PersonalTodoRepository;
import com.module06.backend.calendar.exception.CalendarErrorCode;
import com.module06.backend.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/* comment.
    개인 Todo 유스케이스 구현체. action 도메인처럼 REST 리소스 단위로 서비스 하나에
    묶는다(08/04 팀 협의 원칙, ActionService와 동일).

    연결된 클래스
    - CreateTodoUseCase · ToggleTodoCompleteUseCase : 구현하는 계약
    - PersonalTodoRepository                        : 저장·조회
*/
@Service
@RequiredArgsConstructor
public class PersonalTodoService implements CreateTodoUseCase, ToggleTodoCompleteUseCase {

    private final PersonalTodoRepository personalTodoRepository;

    @Override
    @Transactional
    public PersonalTodo create(CreateTodoCommand command) {
        LocalDate endDate = command.endDate() != null ? command.endDate() : command.date();
        if (endDate.isBefore(command.date())) {
            throw new BusinessException(CalendarErrorCode.TODO_INVALID_DATE_RANGE);
        }

        PersonalTodo todo = PersonalTodo.create(
                command.companyId(), command.memberId(), command.title(), command.date(), endDate);
        return personalTodoRepository.save(todo);
    }

    @Override
    @Transactional
    public PersonalTodo toggleComplete(Long companyId, Long memberId, Long todoId) {
        PersonalTodo todo = personalTodoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(CalendarErrorCode.TODO_NOT_FOUND));

        // 다른 회사·다른 사람의 Todo는 "없다"와 동일하게 취급한다 — 존재 자체를 노출하지 않는다.
        if (!companyId.equals(todo.getCompanyId()) || !memberId.equals(todo.getMemberId())) {
            throw new BusinessException(CalendarErrorCode.TODO_NOT_FOUND);
        }

        todo.toggleDone();
        return personalTodoRepository.save(todo);
    }
}
