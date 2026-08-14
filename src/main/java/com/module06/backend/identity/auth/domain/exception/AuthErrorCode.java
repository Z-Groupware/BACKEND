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
     * 같은 부서 안 역할명 중복(§4-1). 부서·직급과 달리 이 검사는 원래 없었다 — role 에 이름
     * UNIQUE 가 없어 DB 에서 터지지 않았기 때문이다. V2.3.23 이 UK_ROLE_TEAM_NAME 을 세우면서
     * 막지 않으면 DataIntegrityViolationException 이 되고, 그건 사용자가 화면에서 만들 수 있는
     * 입력이므로 400 이어야 한다(ONBOARDING_TEAM_NAME_DUPLICATED 와 같은 이유).
     */
    ONBOARDING_ROLE_NAME_DUPLICATED(HttpStatus.BAD_REQUEST, "AU-044", "같은 부서 안 역할명이 중복되었습니다."),

    /*
     * 역할 지정(§5-1 발급 · §7-4 변경). 없는 역할과 "있지만 다른 부서·다른 회사의 역할"을 가르지
     * 않고 한 코드로 답한다 — 남의 회사 역할 id 를 찍어 봤을 때 404 와 400 이 갈리면 그 id 가
     * 존재한다는 것 자체가 새어 나간다. 화면의 대응도 어느 쪽이든 같다(부서의 역할 목록을 새로
     * 고쳐 그 안에서 다시 고른다). 온보딩의 SUB_TEAM_NOT_IN_TEAM(AU-033)과 코드를 공유하지 않는
     * 이유는, 그쪽은 아직 저장되지 않은 같은 요청 안의 tempId 짝이 안 맞는 경우라 400 이 맞기 때문이다.
     */
    MEMBER_ROLE_LABEL_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-039", "역할을 찾을 수 없습니다."),

    /*
     * 역할 CRUD(§6-10~6-12). ROLE_NOT_FOUND 는 MEMBER_ROLE_LABEL_NOT_FOUND(AU-039)와 코드를
     * 나눈다 — 그쪽은 구성원에게 붙일 역할을 못 찾은 경우고, 여기는 편집 대상 역할 자체가 그
     * 부서에 없는 경우다. 화면의 대응이 다르다(그쪽은 역할 선택을 다시, 여기는 부서 체계 화면을
     * 새로 고쳐야 한다). 다른 부서·남의 회사 역할도 같은 404 로 답하는 것은 AU-039 와 같은
     * 이유다 — 코드가 갈리면 그 id 가 존재한다는 것 자체가 새어 나간다.
     *
     * ROLE_NAME_DUPLICATED 는 같은 부서 안 이름 중복과 시스템 역할명("리더"·"없음") 사용을 함께
     * 받는다. 사용자가 보는 사실이 "그 이름은 이미 있다"로 같고 화면의 대응(다른 이름 입력)도
     * 같다 — 그래서 메시지에 부서를 적지 않는다(시스템 역할은 부서에 속하지 않는다).
     *
     * ROLE_SYSTEM_NOT_MODIFIABLE 은 시드 역할(id 1 리더 · 2 없음, V2.3.9)을 겨냥한다. 그 두 행은
     * company_id·team_id 가 NULL 이라 회사·부서 스코프 조회로는 어차피 안 잡히지만, 그대로 두면
     * 404("없는 역할")로 답하게 된다 — 실제로는 존재하되 건드릴 수 없는 행이므로 403 으로
     * 구분한다(MEMBER_CANNOT_MODIFY_OWNER(AU-026)와 같은 성격이다). 이 둘은 회사가 만든 값이
     * 아니라 id 를 노출해도 새어 나갈 것이 없다.
     */
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "AU-040", "역할을 찾을 수 없습니다."),
    ROLE_NAME_DUPLICATED(HttpStatus.CONFLICT, "AU-041", "이미 있는 역할명입니다."),
    ROLE_IN_USE(HttpStatus.CONFLICT, "AU-042", "해당 역할인 구성원이 있어 삭제할 수 없습니다."),
    ROLE_SYSTEM_NOT_MODIFIABLE(HttpStatus.FORBIDDEN, "AU-043", "시스템 역할은 변경할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
