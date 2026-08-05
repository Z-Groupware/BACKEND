package com.module06.backend.meetingroom.presentation.api.request;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;

/*
 * ROOM-02 회의실 예약 현황 조회의 Query Parameter를 표현하는 프레젠테이션 요청 DTO다.
 *
 * 날짜와 회의실 식별자를 문자열로 받아 이 DTO에서 직접 파싱한다.
 * 타입을 바로 바인딩하면 값이 없거나 형식이 틀렸을 때 Spring의 바인딩 예외가 공통 핸들러의
 * 마지막 분기로 흘러 500이 되므로, 파싱을 요청 DTO가 맡아 명세대로 400 Z-001을 내려준다.
 *
 * 연결된 클래스
 * - MeetingRoomController: 이 DTO로 Query Parameter를 검증한 뒤 조회 조건으로 변환한다
 * - MeetingRoomAvailabilityQuery: 인증 정보와 합쳐 만들어지는 애플리케이션 조회 조건
 *
 * @param date 조회 날짜
 * @param meetingRoomId 특정 회의실만 조회할 때의 식별자, 전체 조회는 null
 */
public record MeetingRoomAvailabilityRequest(
        LocalDate date,
        Long meetingRoomId
) {

    /* 명세가 정한 조회 날짜 형식인 YYYY-MM-DD 포맷터다. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /*
     * Query Parameter 문자열을 검증하고 요청 DTO로 변환한다.
     *
     * @param date 조회 날짜 문자열
     * @param meetingRoomId 회의실 식별자 문자열, 전체 조회는 null 또는 빈 문자열
     * @return 검증을 통과한 요청 DTO
     * @throws BusinessException 날짜가 없거나 형식이 올바르지 않은 경우, 회의실 식별자가 숫자가 아닌 경우
     */
    public static MeetingRoomAvailabilityRequest of(String date, String meetingRoomId) {
        /* 두 파라미터를 각각 검증해 잘못된 입력을 400 Z-001로 통일한다. */
        return new MeetingRoomAvailabilityRequest(parseDate(date), parseMeetingRoomId(meetingRoomId));
    }

    /*
     * 인증 정보와 합쳐 애플리케이션 조회 조건으로 변환한다.
     * 회사와 구성원 식별자는 Access Token principal에서만 오므로 요청 값과 섞이지 않는다.
     *
     * @param companyId 인증 principal에서 추출한 회사 식별자
     * @param memberId 인증 principal에서 추출한 구성원 식별자
     * @return 애플리케이션 계층에 전달할 조회 조건
     */
    public MeetingRoomAvailabilityQuery toQuery(Long companyId, Long memberId) {
        /* 검증된 요청 값과 인증 정보를 하나의 조회 조건으로 묶는다. */
        return new MeetingRoomAvailabilityQuery(companyId, memberId, date, meetingRoomId);
    }

    /*
     * 필수 조회 날짜를 파싱한다.
     *
     * @param date 조회 날짜 문자열
     * @return 파싱된 조회 날짜
     * @throws BusinessException 값이 없거나 YYYY-MM-DD 형식이 아닌 경우
     */
    private static LocalDate parseDate(String date) {
        /* 조회 날짜는 필수이므로 값이 비어 있으면 입력값 오류로 처리한다. */
        if (date == null || date.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            /* 명세가 약속한 형식만 허용해 2026-8-4 같은 변형이 통과하지 않게 한다. */
            return LocalDate.parse(date.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /*
     * 선택 파라미터인 회의실 식별자를 파싱한다.
     *
     * @param meetingRoomId 회의실 식별자 문자열
     * @return 파싱된 회의실 식별자, 값이 없으면 null
     * @throws BusinessException 값이 숫자가 아닌 경우
     */
    private static Long parseMeetingRoomId(String meetingRoomId) {
        /* 값을 넘기지 않은 요청은 활성 회의실 전체 조회를 의미한다. */
        if (meetingRoomId == null || meetingRoomId.isBlank()) {
            return null;
        }

        try {
            /* 존재하지 않는 회의실은 조회 단계에서 404로 처리하고, 여기서는 형식만 검증한다. */
            return Long.valueOf(meetingRoomId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
