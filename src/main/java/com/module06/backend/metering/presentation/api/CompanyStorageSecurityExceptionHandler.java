package com.module06.backend.metering.presentation.api;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

/*
 * 메서드 보안에서 거절된 저장소 관리 요청을 403으로 변환한다 — MeetingRoomSecurityExceptionHandler·
 * CompanyActionSecurityExceptionHandler와 동일한 이유. GlobalExceptionHandler의
 * @ExceptionHandler(Exception.class)가 AuthorizationDeniedException도 잡아버려서(모든 예외를 잡는
 * 최후의 그물이라), 이 워크어라운드가 없으면 OWNER/ADMIN이 아닌 사용자의 @PreAuthorize 거절이
 * 403이 아니라 500(Z-003)으로 나간다 — LEADER/MEMBER가 저장소 관리 화면에 직접 접근했을 때
 * "크래시"로 보이던 원인이 이거였다.
 *
 * 에러코드는 범용 CommonErrorCode.ACCESS_DENIED(Z-002)가 아니라 MeteringErrorCode.MT_FORBIDDEN_SCOPE
 * (MT-004)를 쓴다 — CompanyStoragePlanService.requireOwnerOrAdmin이 저장소 플랜 엔드포인트의
 * 같은 상황(OWNER/ADMIN 아님)에 이미 이 코드를 쓰고 있어서, 저장소 현황 엔드포인트도 같은 코드로
 * 맞춘다(두 정석 사례도 각자 범용 코드 대신 도메인 코드를 새로 만들었다 — 여긴 이미 있어 재사용).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CompanyStorageController.class)
public class CompanyStorageSecurityExceptionHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(MeteringErrorCode.MT_FORBIDDEN_SCOPE.getHttpStatus())
                .body(ErrorResponse.of(
                        MeteringErrorCode.MT_FORBIDDEN_SCOPE,
                        request.getRequestURI(),
                        MDC.get(TRACE_ID_MDC_KEY)
                ));
    }
}
