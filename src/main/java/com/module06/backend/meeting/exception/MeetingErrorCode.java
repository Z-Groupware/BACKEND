package com.module06.backend.meeting.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/*
 * 회의 API에서 사용하는 MT 계열 오류 코드다.
 *
 * 이후 MEET-02~09가 같은 enum을 확장 없이 사용할 수 있도록 확정된 오류 계약 전체를 정의한다.
 */
@Getter
@AllArgsConstructor
public enum MeetingErrorCode implements ErrorCode {

    /* 회의가 없거나 다른 회사 소속인 경우다. */
    MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "MT-001", "존재하지 않는 회의입니다."),

    /* meeting_room_slot 복합 PK 충돌로 예약이 겹친 경우다. */
    MEETING_ROOM_TIME_CONFLICT(HttpStatus.CONFLICT, "MT-002", "해당 시간에 이미 예약된 회의실입니다."),

    /* 종료 시각이 시작 시각보다 늦지 않은 경우다. */
    INVALID_MEETING_TIME_RANGE(HttpStatus.BAD_REQUEST, "MT-003", "회의 종료 시각은 시작 시각보다 늦어야 합니다."),

    /* 시작 또는 종료 시각이 30분 그리드에 맞지 않는 경우다. */
    INVALID_MEETING_SLOT(HttpStatus.BAD_REQUEST, "MT-005", "회의 예약은 30분 단위여야 합니다."),

    /* 회의 담당자가 아닌 사용자가 관리 기능을 호출한 경우다. */
    MEETING_HOST_ONLY(HttpStatus.FORBIDDEN, "MT-006", "회의 담당자만 수행할 수 있습니다."),

    /* 입장 허용 시각보다 먼저 입장을 시도한 경우다. */
    ENTRY_NOT_AVAILABLE(HttpStatus.CONFLICT, "MT-008", "아직 입장할 수 없는 회의입니다."),

    /* 이미 종료된 회의에 상태 변경을 시도한 경우다. */
    MEETING_ALREADY_DONE(HttpStatus.CONFLICT, "MT-009", "이미 종료된 회의입니다."),

    /* 참석자에 다른 회사 또는 삭제된 구성원이 포함된 경우다. */
    INVALID_ATTENDEES(HttpStatus.BAD_REQUEST, "MT-010", "참석자 목록이 올바르지 않습니다."),

    /* 회의 상세 열람 권한이 없는 경우다. */
    MEETING_READ_FORBIDDEN(HttpStatus.FORBIDDEN, "MT-011", "회의 열람 권한이 없습니다."),

    /* 현재보다 과거 시각으로 예약하려는 경우다. */
    PAST_MEETING_TIME(HttpStatus.BAD_REQUEST, "MT-012", "과거 시각으로는 예약할 수 없습니다."),

    /* 시작되지 않은 회의를 종료하려는 경우다. */
    MEETING_NOT_STARTED(HttpStatus.CONFLICT, "MT-013", "시작되지 않은 회의입니다."),

    /* 이미 시작된 회의를 수정하거나 취소하려는 경우다. */
    MEETING_ALREADY_STARTED(HttpStatus.CONFLICT, "MT-014", "이미 시작된 회의는 수정·취소할 수 없습니다."),

    /* 대주제 또는 하나 이상의 소주제 계약이 깨진 경우다. */
    INVALID_MEETING_AGENDA(HttpStatus.BAD_REQUEST, "MT-015", "대주제와 하나 이상의 소주제가 필요합니다."),

    /* 개설자 역할과 상위 팀 액션 입력 정책이 맞지 않는 경우다. */
    INVALID_RELATED_ACTION_POLICY(HttpStatus.BAD_REQUEST, "MT-016", "개설자 역할에 맞는 상위 팀 액션이 필요합니다."),

    /* 개설자 외에 초대한 참석자가 한 명도 없는 경우다. */
    MEETING_ATTENDEE_REQUIRED(HttpStatus.BAD_REQUEST, "MT-017", "개설자 외 참석자를 한 명 이상 선택해야 합니다."),

    /* 상위 팀 액션 자리에 개인 액션을 연결하려는 경우다. */
    RELATED_ACTION_NOT_TEAM_ACTION(HttpStatus.BAD_REQUEST, "MT-018", "상위 팀 액션만 회의에 연결할 수 있습니다."),

    /* 개설자의 소속 팀과 선택한 상위 팀 액션의 팀이 다른 경우다. */
    RELATED_ACTION_TEAM_MISMATCH(HttpStatus.BAD_REQUEST, "MT-019", "같은 팀의 상위 팀 액션만 선택할 수 있습니다.");

    /* 응답에 사용할 HTTP 상태다. */
    private final HttpStatus httpStatus;

    /* 클라이언트가 분기 처리할 서비스 오류 코드다. */
    private final String code;

    /* 사용자에게 전달할 오류 메시지다. */
    private final String message;
}
