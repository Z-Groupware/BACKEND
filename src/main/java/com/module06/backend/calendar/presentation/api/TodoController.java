package com.module06.backend.calendar.presentation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;

import com.module06.backend.calendar.application.command.CreateTodoCommand;
import com.module06.backend.calendar.application.usecase.CreateTodoUseCase;
import com.module06.backend.calendar.application.usecase.ToggleTodoCompleteUseCase;
import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.presentation.api.request.CreateTodoRequest;
import com.module06.backend.calendar.presentation.api.response.TodoResponse;
import com.module06.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/* comment.
    개인 Todo API 진입점. 조작 범위 = 생성·완료토글뿐(2026-08-06 홍근 확인) — 수정·삭제
    엔드포인트는 화면 근거가 없어 만들지 않는다. 조회는 별도 엔드포인트가 없다 —
    GET /api/calendar 통합 조회(CalendarController, 착수 예정)에 포함된다.

    담당 엔드포인트
    - POST  /api/todos                생성 (전 구성원, 본인 소유로 생성)
    - PATCH /api/todos/{todoId}/complete  완료 체크박스 토글 (본인 소유분만)
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    연결된 클래스
    - CreateTodoUseCase · ToggleTodoCompleteUseCase : 호출 대상
    - CreateTodoRequest                              : 입력 DTO
    - TodoResponse                                   : 출력 DTO
*/
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final CreateTodoUseCase createTodoUseCase;
    private final ToggleTodoCompleteUseCase toggleTodoCompleteUseCase;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TodoResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody CreateTodoRequest request
    ) {
        PersonalTodo todo = createTodoUseCase.create(
                new CreateTodoCommand(companyId, memberId, request.title(), request.date()));

        return ApiResponse.created("Todo를 추가했습니다.", TodoResponse.from(todo));
    }

    // 완료 체크박스 토글 — memberId는 토큰에서만 꺼낸다(남의 Todo를 못 건드리게).
    @PatchMapping("/{todoId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TodoResponse> toggleComplete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @PathVariable Long todoId
    ) {
        PersonalTodo todo = toggleTodoCompleteUseCase.toggleComplete(companyId, memberId, todoId);

        return ApiResponse.success("Todo 완료 상태를 변경했습니다.", TodoResponse.from(todo));
    }
}
