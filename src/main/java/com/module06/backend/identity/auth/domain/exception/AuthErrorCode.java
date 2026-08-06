package com.module06.backend.identity.auth.domain.exception;

import org.springframework.http.HttpStatus;

import com.module06.backend.global.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
    identity(인증) 도메인 전용 에러 코드. 접두어는 "AU" 다.
    global.exception.ErrorCode 를 구현해 GlobalExceptionHandler·BusinessException 과 그대로 맞물린다.

    01~05 는 모두 한 로그인 흐름이라 기업코드 조회(company)까지 이 enum 하나로 모은다 —
    도메인 폴더는 나눠도 에러 카탈로그를 쪼개면 프론트가 두 곳을 봐야 한다.

    LOGIN_FAILED 가 회사 없음·이메일 없음·비번 틀림을 전부 흡수하는 것은 의도다.
    구분해서 내리면 계정 존재 여부 확인 도구가 된다.

    REFRESH_TOKEN_EXPIRED 를 따로 두지 않는다. 만료와 위조에 대한 프론트 대응이 모두
    "재로그인"으로 같고, 구분해 내리면 공격자에게 "서명은 맞았다"를 알려준다.

    MEMBER_STATUS_TRANSITION_INVALID 가 409 인 이유: 요청 형식이 틀린 게 아니라(400) 지금 상태와
    충돌하는 것이다. 인수인계 화면을 열어둔 사이 다른 승인자가 먼저 처리한 경우가 대표적이라,
    프론트의 대응은 "새로 고쳐 다시 보기" 다.

    연결된 클래스
    - ErrorCode           : 구현하는 인터페이스 (global.exception)
    - BusinessException   : 이 코드를 담아 던지는 예외 (global.exception)
    - JwtTokenProvider    : UNAUTHORIZED · REFRESH_TOKEN_INVALID 를 던짐 (global.security)
*/
@Getter
@AllArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    COMPANY_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-001", "기업 코드를 찾을 수 없어요. 관리자에게 문의해 주세요."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AU-002", "로그인 정보가 올바르지 않습니다."),
    ACCOUNT_DELETED(HttpStatus.FORBIDDEN, "AU-003", "삭제된 계정입니다. 관리자에게 문의해 주세요."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AU-004", "다시 로그인해 주세요."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "AU-005", "보안을 위해 로그아웃되었습니다. 다시 로그인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AU-006", "로그인이 필요합니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "AU-007", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-008", "구성원을 찾을 수 없습니다."),
    MEMBER_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "AU-009",
            "지금 상태에서는 처리할 수 없습니다. 화면을 새로 고쳐 확인해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
