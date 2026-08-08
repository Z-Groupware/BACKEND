package com.module06.backend.calendar.exception;

import org.springframework.http.HttpStatus;

import com.module06.backend.global.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/* comment.
    캘린더·개인 Todo 도메인 전용 에러 코드. 접두어 "CAL"은 CLAUDE.md 3절 도메인 표에
    아직 없던 신규 배분(2026-08-06)이라 이번에 새로 정한다 — 기존 2글자(AC·PJ 등)와
    안 겹치게 3글자로.

    연결된 클래스
    - ErrorCode          : 구현하는 인터페이스 (global.exception)
    - BusinessException  : 이 코드를 담아 던지는 예외 (global.exception)
*/
@Getter
@AllArgsConstructor
public enum CalendarErrorCode implements ErrorCode {

    // 다른 회사·다른 사람의 Todo id로 접근한 경우도 여기로 뭉갠다 — 존재 여부 자체를
    // 노출하지 않는다(전 구성원 공개인 action과 달리 Todo는 완전히 개인 소유라 더 좁게 막는다).
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "CAL-001", "존재하지 않거나 접근할 수 없는 Todo입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
