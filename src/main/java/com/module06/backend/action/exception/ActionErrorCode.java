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

    NOT_TEAM_LEADER(AC-003)는 FR-AC-07 폐기(2026-08-07)로 한동안 던지는 곳이 없었으나,
    2026-08-11 팀장의 팀원 개인 액션 조회(assigneeMemberId 파라미터) 인가 검증에 재사용됨 —
    코드 자체는 "리더만 가능"이라는 뜻이라 새 용도에도 그대로 맞는다.
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
    ACTION_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "AC-010", "허용되지 않는 상태 전환입니다."),

    // FR-AC-09 — 회의별 조회는 companyId로 액션 행만 스코프하는 것만으로는 다른 회사·존재하지
    // 않는 meetingId를 "액션 없음"과 구분 못 한다. meeting(D)의 공개 조회 포트로 먼저 소유를
    // 확인한다(코드래빗 지적, PR #229 — handover(E)의 MeetingQueryPortDelegatingAdapter와 같은 이유).
    ACTION_MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-011", "존재하지 않거나 접근할 수 없는 회의입니다."),

    // 2026-08-10 — 팀 액션 첨부파일 다운로드 URL 발급에서, 그 팀 액션의 프로젝트 소속이 아닌
    // attachmentId를 넣으면 던진다(project 쪽 PJ-004와 같은 성격이지만 진입 경로가 달라 별도 코드).
    ACTION_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AC-012", "존재하지 않는 첨부파일입니다."),

    // 2026-08-11 — 팀장이 다른 팀 팀원의 개인 액션 목록을 조회하려 했다(팀원 관리 화면,
    // GET /api/actions?assigneeMemberId= IDOR 방지). 대상 memberId 자체는 같은 회사 소속이라
    // 존재를 숨길 이유가 없어 404가 아니라 403 — NOT_TEAM_LEADER·NOT_ACTION_ASSIGNEE와 같은 성격.
    ACTION_ASSIGNEE_OUT_OF_TEAM_SCOPE(HttpStatus.FORBIDDEN, "AC-013", "같은 팀 소속 담당자만 조회할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
