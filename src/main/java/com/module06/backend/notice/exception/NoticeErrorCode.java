package com.module06.backend.notice.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/* 공지 API에서 사용하는 NT 계열 오류 코드다. */
@Getter
@AllArgsConstructor
public enum NoticeErrorCode implements ErrorCode {

    /* 공지가 없거나 삭제됐거나 다른 회사 소속인 경우를 같은 404로 숨긴다. */
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NT-001", "존재하지 않는 공지입니다."),

    /* 같은 회사에서 OWNER·ADMIN이 아닌 사용자가 공지 관리 기능을 호출한 경우다. */
    NOTICE_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "NT-002", "공지를 관리할 권한이 없습니다."),

    /* 제목 또는 본문과 인증 작성 정보가 공지 작성 계약에 맞지 않는 경우다. */
    INVALID_NOTICE_INPUT(HttpStatus.BAD_REQUEST, "NT-003", "입력값이 올바르지 않습니다.");

    /* 응답에 사용할 HTTP 상태다. */
    private final HttpStatus httpStatus;

    /* 클라이언트가 분기 처리할 공지 오류 코드다. */
    private final String code;

    /* 사용자에게 전달할 오류 메시지다. */
    private final String message;
}
