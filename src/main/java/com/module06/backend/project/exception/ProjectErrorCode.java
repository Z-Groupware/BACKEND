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
    /* 2026-08-16 — 사용처 없음. 첨부 삭제 권한을 Owner 하나로 통일하면서 업로더 검사를 없앴다
       (ProjectAttachmentService.delete). 에러코드는 공개 계약이라 지우지 않는다 — 지우면 값이
       다른 뜻으로 재사용된다. */
    NOT_ATTACHMENT_UPLOADER(HttpStatus.FORBIDDEN, "PJ-005", "첨부파일 업로더 본인만 삭제할 수 있습니다."),
    INVALID_TEAM_ASSIGNMENT(HttpStatus.FORBIDDEN, "PJ-006", "소속되지 않은 부서는 지정할 수 없습니다."),

    // 2026-08-10, CodeRabbit(#313) 지적 — confirm이 클라이언트가 보낸 fileUrl(S3 키)을 검증 없이
    // 저장하면, 다른 프로젝트·다른 도메인(cap의 recordings/...)의 키를 자기 첨부로 "확정"한 뒤
    // 삭제 API로 그 실제 S3 객체를 지울 수 있다. ManualRecordingService의 s3Key 접두 검증과
    // 동일 취지.
    ATTACHMENT_KEY_MISMATCH(HttpStatus.BAD_REQUEST, "PJ-007", "첨부파일 업로드 경로가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
