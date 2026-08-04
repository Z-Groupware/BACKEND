package com.module06.backend.action.exception;

import org.springframework.http.HttpStatus;

import com.module06.backend.global.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
    action 도메인 전용 에러 코드. global.exception.ErrorCode를 구현해 GlobalExceptionHandler·
    BusinessException과 그대로 맞물린다(단일 enum이 아니라 도메인별 enum으로 분리 — 담당자별
    파일 충돌 방지). 접두어는 CLAUDE.md 3절 도메인 표의 action 접두어 "AC"를 그대로 따른다
    (윤종호 협의, 08/04 확정).

    연결된 클래스
    - ErrorCode                        : 구현하는 인터페이스 (global.exception)
    - BusinessException                : 이 코드를 담아 던지는 예외 (global.exception)
    - PersonalActionAssigneeOnlyPolicy : NOT_ACTION_ASSIGNEE를 던짐 (application.policy)
    - TeamActionLeaderOnlyPolicy       : NOT_TEAM_LEADER를 던짐 (application.policy)
    - ActionTypeShapePolicy            : INVALID_ACTION_TYPE_SHAPE를 던짐 (domain.policy)
*/
@Getter
@AllArgsConstructor
public enum ActionErrorCode implements ErrorCode {

    ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-001", "존재하지 않는 액션입니다."),
    NOT_ACTION_ASSIGNEE(HttpStatus.FORBIDDEN, "AC-002", "담당자만 수행할 수 있습니다."),
    NOT_TEAM_LEADER(HttpStatus.FORBIDDEN, "AC-003", "해당 팀의 리더만 수행할 수 있습니다."),
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-004", "존재하지 않는 체크리스트 항목입니다."),
    INVALID_ACTION_TYPE_SHAPE(HttpStatus.BAD_REQUEST, "AC-005", "액션 종류(TEAM/PERSONAL)에 맞지 않는 필드 조합입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
