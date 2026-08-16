package com.module06.backend.global.exception;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.module06.backend.global.audit.AuthzAuditLogger;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        // 인가 거부(403)만 감사 기록으로 남긴다. 이 프로젝트의 교차 회사 거부는 시큐리티 필터가 아니라
        // 서비스·컨트롤러의 도메인 규칙(MT_FORBIDDEN_SCOPE·HO_ACCESS_DENIED 등)에서 나오므로,
        // SecurityErrorResponder만 감시하면 정작 필요한 기록이 하나도 남지 않는다.
        // 404·400 등은 남기지 않는다 — 감사 대상이 아니고 로그만 불린다.
        if (errorCode.getHttpStatus() == HttpStatus.FORBIDDEN) {
            AuthzAuditLogger.deniedByDomain(request, errorCode.getCode());
        } else if (errorCode == AuthErrorCode.LOGIN_FAILED) {
            // 401 을 통째로 남기지 않고 이 하나만 고른다. REFRESH_TOKEN_INVALID 는 액세스 토큰이
            // 만료돼 재발급을 부르는 정상 트래픽에서 늘 나오므로, 함께 남기면 로그인 실패가 그
            // 잡음에 묻힌다. 감사 기록은 "평소에 안 나오는 것"이어야 신호가 된다.
            // 같은 401 인 REFRESH_TOKEN_REUSED 는 AuthService 가 직접 남긴다 — 여기서는
            // 누구의 표였는지(memberId)를 알 수 없기 때문이다.
            AuthzAuditLogger.loginFailed(request, errorCode.getCode());
        }
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI(), currentTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                     HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        // 요청 본문(@RequestBody) 검증 실패. 지금까지 이 400 은 응답으로만 나가고 로그에 한 줄도
        // 남지 않아(액세스 로그도 없다) "어느 요청의 어느 필드가 왜 튕겼나"를 사후에 되짚을 수
        // 없었다. 재현 없이 짚도록 URI·필드·사유를 남긴다.
        logBadRequest("요청 본문 검증 실패", request, "필드오류=[" + formatFieldErrors(details) + "]");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId(), details));
    }

    /**
     * 컨트롤러의 {@code @RequestParam}·{@code @PathVariable}에 붙은 {@code @Min}·{@code @Max} 같은
     * 제약이 깨졌을 때(클래스에 {@code @Validated} 필요). {@code @RequestBody} 검증 실패는
     * {@link MethodArgumentNotValidException} 이 따로 잡는다 — 이건 그 밖의 메서드 파라미터용이다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        logBadRequest("파라미터 검증 실패", request, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    /** AOP 기반 메서드 검증({@code MethodValidationPostProcessor})을 쓰는 곳이 생기면 여기로 온다. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        logBadRequest("제약 위반", request, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                          HttpServletRequest request) {
        logBadRequest("잘못된 인자", request, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
                                                                                  HttpServletRequest request) {
        // 본문 자체가 JSON 으로 파싱되지 않은 경우(형 불일치·깨진 payload 등). 원인 메시지는
        // 가장 구체적인 cause 로 남긴다 — 최상위 메시지는 래퍼라 실제 이유가 묻힌다.
        logBadRequest("본문 파싱 실패", request, ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), currentTraceId()));
    }

    /**
     * 메서드 시큐리티({@code @PreAuthorize}) 역할 거부. 던져지는
     * {@code AuthorizationDeniedException} 은 {@code AccessDeniedException} 의 하위 타입이라
     * 여기서 함께 잡힌다. 이 예외는 필터가 아니라 컨트롤러 메서드 호출 중에 나므로
     * ExceptionTranslationFilter 의 accessDeniedHandler({@code SecurityErrorResponder})가 아니라
     * DispatcherServlet 의 이 advice 로 흘러온다. 전용 핸들러가 없으면 아래 catch-all 이
     * 500(Z-003)으로 삼켜 "권한 없음"이 서버 오류로 둔갑한다.
     *
     * <p>감사 기록은 {@code SecurityErrorResponder.handle} 과 같은 {@code deniedByFilter} 로 남긴다 —
     * 그 outcome 의 정의가 "필터 체인·{@code @PreAuthorize} 단계 거부"라 계층과 무관하게 일관된다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                       HttpServletRequest request) {
        AuthzAuditLogger.deniedByFilter(request, CommonErrorCode.ACCESS_DENIED.getCode());
        return ResponseEntity.status(CommonErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(ErrorResponse.of(CommonErrorCode.ACCESS_DENIED, request.getRequestURI(), currentTraceId()));
    }

    /**
     * 마지막 그물. 응답 본문에는 traceId 만 나가고 원인은 숨기지만(정보 노출 방지), 서버 로그에는
     * 반드시 스택트레이스를 남긴다 — 남기지 않으면 Z-003 이 나간 뒤 원인을 되짚을 방법이 아예
     * 없어진다(응답의 traceId 로 대조할 로그 라인 자체가 없다).
     *
     * <p>traceId 를 메시지에 직접 박는다. logback 설정 파일이 없어 Spring 기본 패턴을 쓰는데,
     * 그 패턴에는 MDC 가 들어 있지 않아 {@code %X{traceId}} 로는 찍히지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외 — traceId={} {} {}",
                currentTraceId(), request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI(), currentTraceId()));
    }

    private String currentTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }

    /**
     * 클라이언트 오류(400)를 한 줄로 남긴다. 서버 오류(500)와 달리 스택트레이스는 남기지 않되,
     * catch-all(500)과 같은 형식(traceId·메서드·URI)을 유지해 응답의 traceId 로 로그를 대조할 수
     * 있게 한다. 레벨은 warn 이다 — 서버 결함이 아니라 잘못 들어온 요청이므로 error 로 올리면
     * 진짜 장애 신호에 잡음을 섞는다.
     */
    private void logBadRequest(String kind, HttpServletRequest request, String detail) {
        log.warn("{}(400) — traceId={} {} {} {}",
                kind, currentTraceId(), request.getMethod(), request.getRequestURI(), detail);
    }

    private static String formatFieldErrors(List<ErrorResponse.FieldErrorDetail> details) {
        return details.stream()
                .map(d -> d.field() + "='" + d.reason() + "'")
                .collect(Collectors.joining(", "));
    }
}
