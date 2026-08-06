package com.module06.backend.action.presentation.api.request;

import java.time.LocalDate;

import com.module06.backend.action.domain.model.ActionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/* comment.
    액션 수동 추가 요청 DTO(FR-AC-01 예외 경로). 담을 값: 프로젝트 id · 종류(TEAM/PERSONAL) ·
    팀 id(TEAM일 때) 또는 담당자 id(PERSONAL일 때) · 제목 · 설명 · 마감일.
    종류별 필드 조합 검증은 여기서 1차로 하지 않는다 — ActionTypeShapePolicy(domain.policy)의 책임.

    연결된 클래스
    - ActionController   : 이 DTO를 받는 진입점
    - CreateActionCommand : 이 DTO가 변환되는 application 명령
*/
public record CreateActionRequest(
        @NotNull Long projectId,
        @NotNull ActionType actionType,
        Long teamId,
        Long assigneeMemberId,
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull LocalDate dueDate
) {
}
