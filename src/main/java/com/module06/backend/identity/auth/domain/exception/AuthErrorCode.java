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
            "지금 상태에서는 처리할 수 없습니다. 화면을 새로 고쳐 확인해 주세요."),

    /*
     * 기업 등록(API 27). 승인 절차가 없어 신청 하나가 기업·오너 계정 생성까지 처리하므로,
     * 여기서 막지 못한 값은 그대로 회사가 되어 되돌릴 경로가 없다.
     */
    REGISTRATION_NO_INVALID(HttpStatus.BAD_REQUEST, "AU-010",
            "사업자등록번호 형식이 올바르지 않습니다. 000-00-00000 형태로 입력해 주세요."),
    REGISTRATION_NO_DUPLICATED(HttpStatus.CONFLICT, "AU-011",
            "이미 등록된 사업자등록번호입니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "AU-012",
            "이용약관과 개인정보 처리방침에 동의해 주세요."),

    /*
     * 기업코드를 3회 뽑았는데 전부 UNIQUE 에 걸린 경우. 32^8 조합에서 실제로 일어나면
     * 무작위 원천이 고장 난 것이므로 재시도로 덮지 않고 500 으로 드러낸다.
     */
    COMPANY_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AU-013",
            "기업 코드 발급에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    /*
     * 부서 CRUD(API 14~17). 본부→팀 2단계까지만 허용하고, 같은 부모 안에서만 이름 중복을 막는다
     * (§6-2). 전역 유니크가 아니므로 다른 본부 아래에는 같은 이름의 팀이 있을 수 있다.
     */
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-014", "부서를 찾을 수 없습니다."),
    TEAM_NAME_DUPLICATED(HttpStatus.CONFLICT, "AU-016", "이미 있는 부서 이름입니다."),
    TEAM_HAS_MEMBERS(HttpStatus.CONFLICT, "AU-017", "소속된 구성원이 있어 삭제할 수 없습니다."),
    TEAM_HAS_PROJECTS(HttpStatus.CONFLICT, "AU-019", "연결된 프로젝트가 있어 삭제할 수 없습니다."),

    /*
     * 직급 CRUD(§6-6~6-9). POSITION_ROLE_NOT_ASSIGNABLE 은 member 발급 흐름(§5-1)의
     * 동명 규칙과 코드를 공유하지 않는다 — 그쪽은 403(OWNER 승격 우회 차단), 여기는
     * 400(입력값 검증)으로 HTTP 상태가 달라 같은 enum 상수를 못 쓴다.
     */
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-020", "직급을 찾을 수 없습니다."),
    POSITION_ROLE_NOT_ASSIGNABLE(HttpStatus.BAD_REQUEST, "AU-021", "직급에는 리더 또는 멤버 권한만 지정할 수 있습니다."),
    POSITION_NAME_DUPLICATED(HttpStatus.CONFLICT, "AU-022", "이미 있는 직급명입니다."),
    POSITION_IN_USE(HttpStatus.CONFLICT, "AU-023", "해당 직급인 구성원이 있어 삭제할 수 없습니다."),

    /*
     * 구성원 관리(§7). MEMBER_ROLE_NOT_ASSIGNABLE 은 직급 CRUD(§6-7)의 POSITION_ROLE_NOT_ASSIGNABLE 과
     * 코드를 공유하지 않는다 — 그쪽은 400(입력값 검증), 여기는 403(OWNER 승격 우회 차단)으로
     * HTTP 상태가 달라 같은 enum 상수를 못 쓴다(POSITION_ROLE_NOT_ASSIGNABLE 주석과 같은 이유).
     */
    MEMBER_ROLE_NOT_ASSIGNABLE(HttpStatus.FORBIDDEN, "AU-024", "구성원에게는 리더 또는 멤버 권한만 지정할 수 있습니다."),
    MEMBER_FIELD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "AU-025", "이 요청으로는 변경할 수 없는 값이 포함되어 있습니다."),
    MEMBER_CANNOT_MODIFY_OWNER(HttpStatus.FORBIDDEN, "AU-026", "오너의 정보는 변경할 수 없습니다."),
    MEMBER_CANNOT_MODIFY_SELF(HttpStatus.FORBIDDEN, "AU-027", "본인 정보는 이 화면에서 변경할 수 없습니다."),
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AU-028", "이미 등록된 이메일입니다."),
    MEMBER_TEAM_LEADER_ALREADY_EXISTS(HttpStatus.CONFLICT, "AU-029", "해당 부서에 이미 팀장이 있습니다."),
    MEMBER_SEAT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "AU-030", "이용 중인 요금제의 좌석 수를 초과했습니다."),
    MEMBER_OWNER_CANNOT_BE_ADMIN(HttpStatus.BAD_REQUEST, "AU-031", "오너는 관리자 권한 부여 대상이 아닙니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
