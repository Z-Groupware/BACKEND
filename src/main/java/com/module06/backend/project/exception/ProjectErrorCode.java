package com.module06.backend.project.exception;

import org.springframework.http.HttpStatus;

import com.module06.backend.global.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
    project 도메인 전용 에러 코드. global.exception.ErrorCode를 구현해 GlobalExceptionHandler·
    BusinessException과 그대로 맞물린다(단일 enum이 아니라 도메인별 enum으로 분리 — 담당자별
    파일 충돌 방지). 접두어는 CLAUDE.md 3절 도메인 표의 project 접두어 "PJ"를 그대로 따른다
    (윤종호 협의, 08/04 확정).

    연결된 클래스
    - ErrorCode              : 구현하는 인터페이스 (global.exception)
    - BusinessException      : 이 코드를 담아 던지는 예외 (global.exception)
    - ProjectOwnerOnlyPolicy : NOT_PROJECT_OWNER를 던짐 (application.policy)
*/
@Getter
@AllArgsConstructor
public enum ProjectErrorCode implements ErrorCode {

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PJ-001", "존재하지 않는 프로젝트입니다."),
    NOT_PROJECT_OWNER(HttpStatus.FORBIDDEN, "PJ-002", "프로젝트 소유자만 수행할 수 있습니다."),
    PROJECT_TAG_DUPLICATE(HttpStatus.CONFLICT, "PJ-003", "이미 사용 중인 프로젝트 태그입니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PJ-004", "존재하지 않는 첨부파일입니다."),
    NOT_ATTACHMENT_UPLOADER(HttpStatus.FORBIDDEN, "PJ-005", "첨부파일 업로더 본인만 삭제할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
