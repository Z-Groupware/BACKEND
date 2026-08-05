package com.module06.backend.capture.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/*
 * 캡처 파이프라인(도메인 A) 전용 에러 코드다.
 *
 * 도메인별 enum 으로 분리해 담당자 간 파일 충돌을 막는다(MR-·PJ- 규약과 같은 방식).
 * 코드 문자열은 API 명세의 표를 그대로 따른다.
 */
@Getter
@AllArgsConstructor
public enum CaptureErrorCode implements ErrorCode {

    /* ANLZ-01 — 같은 회의의 분석이 이미 돌고 있다. 오류처럼 보이지만 중복 방어가 동작한 것이다. */
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "MEETING_409_3", "분석이 이미 진행 중입니다."),

    /* ANLZ-01 — 이미 완료된 분석을 force 없이 다시 돌리려 한 경우다. 재과금을 막는다. */
    ANALYSIS_ALREADY_DONE(HttpStatus.CONFLICT, "MEETING_409_4", "이미 분석이 완료된 회의입니다."),

    /* CAP-06 · ANLZ-03 — 아직 분석하지 않았거나 다른 회사 회의다. 타 회사 리소스는 403이 아니라 404로 존재를 숨긴다. */
    SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "ANLZ-001", "회의 요약이 없습니다."),

    /*
     * 계층 호출이 실패해 분석이 멈췄다.
     *
     * 502 인 이유 — 우리 요청이 잘못된 것이 아니라 뒤에 있는 AI 서버가 응답하지 못한 것이다.
     * 500 으로 내리면 이 저장소의 버그와 구분되지 않고, 알람이 엉뚱한 사람에게 간다.
     */
    ANALYSIS_LAYER_FAILED(HttpStatus.BAD_GATEWAY, "ANLZ-002", "분석 계층 호출에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
