package com.module06.backend.search.domain.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/*
 * 통합 검색 API에서 사용하는 SR 계열 오류 코드다.
 */
@Getter
@AllArgsConstructor
public enum SearchErrorCode implements ErrorCode {

    /* 검색어가 비어 있거나 공백만 전달된 경우다. */
    BLANK_QUERY(HttpStatus.BAD_REQUEST, "SR-001", "검색어는 1자 이상이어야 합니다."),

    /* type 또는 limit 등 검색 Query Parameter가 명세 범위를 벗어난 경우다. */
    INVALID_SEARCH_PARAMETER(HttpStatus.BAD_REQUEST, "SR-002", "검색 요청 값이 올바르지 않습니다."),

    /* 최근 본 항목 type이 검색 대상 타입이 아닌 경우다. */
    INVALID_RECENT_VIEW_TYPE(HttpStatus.BAD_REQUEST, "SR-003", "최근 본 항목 타입이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
