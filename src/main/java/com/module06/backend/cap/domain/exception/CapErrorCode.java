package com.module06.backend.cap.domain.exception;

import com.module06.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

// CAP 도메인 전용 에러코드. BusinessException과 함께 던지면 GlobalExceptionHandler가 공통 처리한다.
@Getter
@AllArgsConstructor
public enum CapErrorCode implements ErrorCode {

    CAP_PART_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "CAP-001", "청크 크기 상한을 초과했습니다."),
    CAP_REQUIRED_ID(HttpStatus.BAD_REQUEST, "CAP-006", "필수 식별자가 누락되었습니다."),
    CAP_REQUIRED_TEXT(HttpStatus.BAD_REQUEST, "CAP-007", "필수 텍스트가 누락되었습니다."),
    CAP_INVALID_PART_SIZE(HttpStatus.BAD_REQUEST, "CAP-008", "청크 크기가 올바르지 않습니다."),
    CAP_PART_KEY_MISMATCH(HttpStatus.BAD_REQUEST, "CAP-009", "청크 경로가 서버가 발급한 값과 일치하지 않습니다."),

    CAP_MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "CAP-002", "회의를 찾을 수 없습니다."),

    CAP_STORAGE_QUOTA_EXCEEDED(HttpStatus.FORBIDDEN, "CAP-003", "저장 용량 한도를 초과했습니다."),
    CAP_NOT_CURRENT_RECORDER(HttpStatus.FORBIDDEN, "CAP-004", "현재 녹음자가 아닙니다."),
    CAP_NOT_ATTENDEE(HttpStatus.FORBIDDEN, "CAP-010", "회의 참석자가 아닙니다."),

    CAP_PART_ALREADY_REGISTERED(HttpStatus.CONFLICT, "CAP-005", "이미 등록된 청크입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
