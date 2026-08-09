package com.module06.backend.meeting.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/*
 * D 도메인의 캡처 세션 API가 사용하는 CS 계열 오류 코드다.
 */
@Getter
@AllArgsConstructor
public enum CaptureSessionErrorCode implements ErrorCode {

    /* 회의에 아직 캡처 세션이 생성되지 않아 상태를 제어할 수 없는 경우다. */
    CAPTURE_SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CS-001",
            "진행 중인 캡처 세션이 없습니다."
    ),

    /* 회의당 하나인 캡처 세션이 이미 생성된 경우다. */
    CAPTURE_SESSION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "CS-002",
            "이미 캡처 세션이 시작된 회의입니다."
    ),

    /* 회의 개설자가 아닌 인증 사용자가 세션 제어를 시도한 경우다. */
    CAPTURE_SESSION_HOST_ONLY(
            HttpStatus.FORBIDDEN,
            "CS-003",
            "회의 개설자만 수행할 수 있습니다."
    ),

    /* PAUSED 세션에 일시정지를 다시 요청한 경우다. */
    CAPTURE_SESSION_ALREADY_PAUSED(
            HttpStatus.CONFLICT,
            "CS-004",
            "이미 일시정지된 캡처 세션입니다."
    ),

    /* ACTIVE 세션에 재개를 다시 요청한 경우다. */
    CAPTURE_SESSION_ALREADY_ACTIVE(
            HttpStatus.CONFLICT,
            "CS-005",
            "이미 녹음 중인 캡처 세션입니다."
    ),

    /* ENDED 세션의 상태 변경을 요청한 경우다. */
    CAPTURE_SESSION_ALREADY_ENDED(
            HttpStatus.CONFLICT,
            "CS-006",
            "이미 종료된 캡처 세션입니다."
    );

    /* 응답에 사용할 HTTP 상태다. */
    private final HttpStatus httpStatus;

    /* 클라이언트가 분기 처리할 캡처 세션 오류 코드다. */
    private final String code;

    /* 사용자에게 전달할 오류 메시지다. */
    private final String message;
}
