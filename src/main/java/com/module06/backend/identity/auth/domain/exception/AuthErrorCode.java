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
    MEMBER_OWNER_CANNOT_BE_ADMIN(HttpStatus.BAD_REQUEST, "AU-031", "오너는 관리자 권한 부여 대상이 아닙니다."),
    /* AU-032 는 비어 있다 — 계정 발급이 역할을 못 받던 시절의 MEMBER_ROLE_LABEL_NOT_SUPPORTED 였다
     * (2026-08-14 발급이 roleId 를 받게 되며 삭제). 코드는 공개 계약이라 다른 뜻으로 재사용하지 않는다. */

    /*
     * 온보딩 커밋(§4-1). TEAM_LEADER_DUPLICATED 는 §7 의 MEMBER_TEAM_LEADER_ALREADY_EXISTS(AU-029)와
     * 코드를 공유하지 않는다 — 그쪽은 409(이미 배정된 리더와 충돌), 여기는 400(같은 요청 안에서
     * 팀당 리더 직급을 둘 이상 받은 입력값 자체가 잘못됨)으로 HTTP 상태가 달라
     * POSITION_ROLE_NOT_ASSIGNABLE 주석과 같은 이유로 상수를 나눈다.
     */
    SUB_TEAM_NOT_IN_TEAM(HttpStatus.BAD_REQUEST, "AU-033", "선택한 역할이 해당 부서에 속하지 않습니다."),
    TEAM_LEADER_DUPLICATED(HttpStatus.BAD_REQUEST, "AU-034", "부서마다 팀장 직급은 한 명만 지정할 수 있습니다."),
    ALREADY_ONBOARDED(HttpStatus.CONFLICT, "AU-035", "이미 온보딩이 완료된 기업입니다."),

    /*
     * 온보딩 요청 안의 중복(§4-1). 막지 않으면 전부 500(Z-003)으로 샌다 — tempId 중복은
     * Collectors.toMap 의 IllegalStateException 으로, 이름 중복은 UK_TEAM_COMPANY_NAME ·
     * UK_POSITION_COMPANY_NAME 위반(DataIntegrityViolationException)으로 터진다.
     * 둘 다 사용자가 화면에서 만들 수 있는 입력이므로 400 이어야 한다.
     */
    ONBOARDING_TEMP_ID_DUPLICATED(HttpStatus.BAD_REQUEST, "AU-036", "임시 식별자가 중복되었습니다."),
    ONBOARDING_TEAM_NAME_DUPLICATED(HttpStatus.BAD_REQUEST, "AU-037", "부서명이 중복되었습니다."),
    ONBOARDING_POSITION_NAME_DUPLICATED(HttpStatus.BAD_REQUEST, "AU-038", "직급명이 중복되었습니다."),

    /*
     * 역할 지정(§5-1 발급 · §7-4 변경). 없는 역할과 "있지만 다른 부서·다른 회사의 역할"을 가르지
     * 않고 한 코드로 답한다 — 남의 회사 역할 id 를 찍어 봤을 때 404 와 400 이 갈리면 그 id 가
     * 존재한다는 것 자체가 새어 나간다. 화면의 대응도 어느 쪽이든 같다(부서의 역할 목록을 새로
     * 고쳐 그 안에서 다시 고른다). 온보딩의 SUB_TEAM_NOT_IN_TEAM(AU-033)과 코드를 공유하지 않는
     * 이유는, 그쪽은 아직 저장되지 않은 같은 요청 안의 tempId 짝이 안 맞는 경우라 400 이 맞기 때문이다.
     */
    MEMBER_ROLE_LABEL_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-039", "역할을 찾을 수 없습니다."),

    /*
     * 마이페이지 비밀번호 변경(PATCH /api/auth/me/password).
     *
     * 넷 다 401 이 아니라 400 이다. 이 프로젝트의 401 은 전부 "다시 로그인해라" 신호이므로
     * (AU-004·005·006), 현재 비밀번호를 틀렸다고 401 을 내리면 프론트 인터셉터가 토큰 만료로
     * 오해해서 멀쩡한 세션을 끊는다. 여기서 틀린 것은 토큰이 아니라 입력값이다.
     *
     * 로그인(LOGIN_FAILED)처럼 하나로 합치지 않는 이유: 여기는 이미 본인이 인증된 자리라
     * 구분해 답해도 남의 계정 정보가 새지 않고, 화면이 어느 입력칸에 오류를 붙일지 알아야 한다.
     *
     * NEW_PASSWORD_SAME_AS_CURRENT 와 PASSWORD_ALREADY_USED 를 나누는 것도 화면 때문이다 —
     * 사용자가 "지금 쓰는 것"과 "예전에 쓰던 것"을 구분해 들으면 다음 시도가 달라진다.
     */
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "AU-040", "새 비밀번호가 서로 일치하지 않습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "AU-041", "현재 비밀번호가 올바르지 않습니다."),
    NEW_PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "AU-042", "지금 쓰고 있는 비밀번호와 같습니다."),
    PASSWORD_ALREADY_USED(HttpStatus.BAD_REQUEST, "AU-043", "이전에 사용한 적이 있는 비밀번호입니다."),

    /*
     * 비밀번호 찾기(POST /api/auth/password/reset).
     *
     * PASSWORD_RESET_ACCOUNT_NOT_FOUND 는 LOGIN_FAILED 와 달리 계정 존재를 드러낸다 — 의도한
     * 선택이다. 기업 코드를 함께 받으므로 유효한 기업 코드를 이미 알아야 여기까지 올 수 있고,
     * 기업 코드 조회에는 이미 분당 20회 제한이 걸려 있다(CompanyCodeGenerator 의 전제).
     * 퇴사·삭제된 계정도 이 코드로 답한다 — 403 을 주면 "그 사람 퇴사했다"를 로그인 없이 알려준다.
     *
     * PASSWORD_RESET_MAIL_FAILED 가 503 인 이유: 요청은 정상이고 우리 메일 경로가 지금 안 되는
     * 것이다. 이 응답이 나갔다면 비밀번호는 바뀌지 않았다(발송 성공을 확인한 뒤에만 저장한다).
     */
    PASSWORD_RESET_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-044",
            "등록되지 않은 계정입니다. 기업 코드와 이메일을 확인해 주세요."),
    PASSWORD_RESET_MAIL_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AU-045",
            "메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요. 기존 비밀번호는 그대로 쓸 수 있어요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
