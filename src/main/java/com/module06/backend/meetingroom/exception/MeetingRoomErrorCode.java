package com.module06.backend.meetingroom.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/*
 * 회의실 도메인 전용 에러 코드다.
 *
 * global.exception.ErrorCode를 구현해 BusinessException·GlobalExceptionHandler와 그대로 맞물리며,
 * 도메인별 enum으로 분리해 담당자 간 파일 충돌을 막는다(project 도메인의 PJ- 규약과 동일한 방식).
 * 접두어 MR-은 API 명세의 회의실 에러 코드 표를 그대로 따른다.
 *
 * 연결된 클래스
 * - ErrorCode: 구현하는 공통 인터페이스
 * - BusinessException: 이 코드를 담아 던지는 예외
 * - MeetingRoomAvailabilityService: ROOM-02에서 MEETING_ROOM_NOT_FOUND를 던진다
 */
@Getter
@AllArgsConstructor
public enum MeetingRoomErrorCode implements ErrorCode {

    /* ROOM-02·04·05 — 조회·수정·삭제 대상이 없거나 다른 회사 소속인 경우다. 타 회사 리소스는 403이 아니라 404로 응답해 존재 여부를 숨긴다. */
    MEETING_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "MR-001", "존재하지 않는 회의실입니다."),

    /* ROOM-03·04 — 같은 회사의 활성 회의실 이름이 중복된 경우다. */
    MEETING_ROOM_NAME_DUPLICATE(HttpStatus.CONFLICT, "MR-002", "이미 사용 중인 회의실 이름입니다."),

    /* ROOM-04·05 — OWNER·ADMIN이 아닌 요청자가 회의실을 관리하려는 경우다. */
    MEETING_ROOM_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "MR-004", "회의실 관리 권한이 없습니다."),

    /* ROOM-05 — 미래 SCHEDULED 예약이 남아 있어 비활성화할 수 없는 경우다. */
    MEETING_ROOM_HAS_RESERVATION(HttpStatus.CONFLICT, "MR-005", "예약이 남아 있는 회의실은 비활성화할 수 없습니다.");

    /* 예외를 응답으로 변환할 때 사용할 HTTP 상태다. */
    private final HttpStatus httpStatus;

    /* 클라이언트가 분기 처리에 사용하는 에러 코드 문자열이다. */
    private final String code;

    /* 사용자에게 노출되는 기본 메시지다. */
    private final String message;
}
