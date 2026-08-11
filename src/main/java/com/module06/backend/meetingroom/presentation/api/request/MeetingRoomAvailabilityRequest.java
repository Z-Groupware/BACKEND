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
 * @param date 조회 주의 기준일, 생략하면 서비스가 KST 오늘을 사용한다
 * @param meetingRoomId 주간 현황을 조회할 필수 회의실 식별자
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
     * @param meetingRoomId 필수 회의실 식별자 문자열
     * @return 검증을 통과한 요청 DTO
     * @throws BusinessException 날짜 형식이 올바르지 않거나 회의실 식별자가 없거나 양수가 아닌 경우
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
     * 선택 조회 기준일을 파싱한다.
     *
     * @param date 조회 날짜 문자열
     * @return 파싱된 기준일, 값이 없으면 null
     * @throws BusinessException 값이 YYYY-MM-DD 형식이 아닌 경우
     */
    private static LocalDate parseDate(String date) {
        /* 기준일을 생략하면 서비스가 KST Clock의 오늘을 사용하도록 null을 유지한다. */
        if (date == null || date.isBlank()) {
            return null;
        }

        try {
            /* 명세가 약속한 형식만 허용해 2026-8-4 같은 변형이 통과하지 않게 한다. */
            return LocalDate.parse(date.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /*
     * 필수 파라미터인 회의실 식별자를 파싱한다.
     *
     * @param meetingRoomId 회의실 식별자 문자열
     * @return 파싱된 양수 회의실 식별자
     * @throws BusinessException 값이 없거나 양수가 아닌 경우
     */
    private static Long parseMeetingRoomId(String meetingRoomId) {
        /* 단일 회의실 주간 조회이므로 식별자를 생략한 요청은 입력값 오류다. */
        if (meetingRoomId == null || meetingRoomId.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            /* 존재 여부는 조회 단계에서 판단하고, 여기서는 양수 식별자 형식만 허용한다. */
            Long parsedMeetingRoomId = Long.valueOf(meetingRoomId.trim());
            if (parsedMeetingRoomId <= 0) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
            }
            return parsedMeetingRoomId;
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
