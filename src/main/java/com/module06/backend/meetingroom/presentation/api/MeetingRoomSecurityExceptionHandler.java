package com.module06.backend.meetingroom.presentation.api;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * 메서드 보안에서 거절된 회의실 관리 요청을 ROOM-04 명세의 오류 응답으로 변환한다.
 * 공통 예외 처리기가 AuthorizationDeniedException을 500으로 처리하지 않도록 회의실 API에만 우선 적용한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MeetingRoomCommandController.class)
public class MeetingRoomSecurityExceptionHandler {

    /* 추적 ID를 ErrorResponse에 동일하게 전달하기 위한 MDC 키다. */
    private static final String TRACE_ID_MDC_KEY = "traceId";

    /* OWNER 또는 ADMIN이 아닌 사용자의 회의실 수정 요청을 MR-004로 응답한다. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        /* HTTP 403과 회의실 관리 권한 오류 코드를 함께 반환한다. */
        return ResponseEntity.status(MeetingRoomErrorCode.MEETING_ROOM_MANAGEMENT_FORBIDDEN.getHttpStatus())
                .body(ErrorResponse.of(
                        MeetingRoomErrorCode.MEETING_ROOM_MANAGEMENT_FORBIDDEN,
                        request.getRequestURI(),
                        MDC.get(TRACE_ID_MDC_KEY)
                ));
    }
}
