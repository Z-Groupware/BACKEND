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
    - ActionTypeShapePolicy            : INVALID_ACTION_TYPE_SHAPE를 던짐 (domain.policy)

    NOT_TEAM_LEADER(AC-003)는 FR-AC-07 폐기(2026-08-07)로 던지는 곳이 없다. 이미 문서화된
    코드라 enum 값 자체는 남겨둔다 — 죽은 건 TeamActionLeaderOnlyPolicy 쪽이었다.
*/
@Getter
@AllArgsConstructor
public enum ActionErrorCode implements ErrorCode {

    ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-001", "존재하지 않는 액션입니다."),
    NOT_ACTION_ASSIGNEE(HttpStatus.FORBIDDEN, "AC-002", "담당자만 수행할 수 있습니다."),
    NOT_TEAM_LEADER(HttpStatus.FORBIDDEN, "AC-003", "해당 팀의 리더만 수행할 수 있습니다."),
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-004", "존재하지 않는 체크리스트 항목입니다."),
    INVALID_ACTION_TYPE_SHAPE(HttpStatus.BAD_REQUEST, "AC-005", "액션 종류(TEAM/PERSONAL)에 맞지 않는 필드 조합입니다."),
    ACTION_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-006", "존재하지 않거나 접근할 수 없는 프로젝트입니다."),
    ACTION_TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-007", "존재하지 않거나 접근할 수 없는 팀입니다."),
    ACTION_ASSIGNEE_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-008", "존재하지 않거나 접근할 수 없는 담당자입니다."),

    /* RVW-04 — AI가 만든 액션을 지우려 했다. 검토(A)가 409로 먼저 막지만, 이 경계를 지나면
       review_log에 남길 판정 대상 자체가 사라져 되돌릴 수 없어 여기서도 본다(2026-08-07). */
    ACTION_DELETE_NOT_MANUAL(HttpStatus.CONFLICT, "AC-009", "직접 추가한 액션만 삭제할 수 있습니다."),
    // develop 머지 중 AC-009가 이미 ACTION_DELETE_NOT_MANUAL(RVW-04)에 쓰이고 있어 AC-010으로 밀림(2026-08-07).
    ACTION_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "AC-010", "허용되지 않는 상태 전환입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
