package com.module06.backend.meetingroom.presentation.api.response;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/*
 * 회의실 API 응답의 날짜·시각 문자열 계약을 한 곳에서 관리하는 프레젠테이션 유틸리티다.
 *
 * 명세가 정한 형식은 날짜 YYYY-MM-DD, 시각 HH:mm이며 오프셋 없는 KST 로컬 시각이다.
 * 응답 DTO마다 포맷터를 따로 두면 엔드포인트가 늘어날 때 형식이 갈라지므로 변환을 여기로 모은다.
 *
 * 연결된 클래스
 * - MeetingRoomItemResponse: 회의실 이용 가능 시각 변환에 사용한다
 * - MeetingRoomAvailabilityResponse: 조회 날짜 변환에 사용한다
 * - MeetingRoomSlotResponse: 슬롯 시작 시각 변환에 사용한다
 */
public final class ApiTimeFormat {

    /* 시각 응답 형식인 두 자리 시와 분 포맷터다. */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /* 날짜 응답 형식인 YYYY-MM-DD 포맷터다. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /*
     * 정적 변환만 제공하므로 인스턴스 생성을 막는다.
     */
    private ApiTimeFormat() {
    }

    /*
     * 필수 시각 값을 HH:mm 문자열로 변환한다.
     *
     * @param time 문자열로 변환할 시각
     * @return HH:mm 형식의 시각 문자열
     * @throws DateTimeException 필수 시각 값이 없는 경우
     */
    public static String formatTime(LocalTime time) {
        /* 잘못된 영속성 값이 정상 응답으로 위장하지 않도록 명확한 변환 오류를 발생시킨다. */
        if (time == null) {
            throw new DateTimeException("회의실 API 응답의 시각은 null일 수 없습니다.");
        }

        /* 검증된 시각을 공통 포맷터로 변환한다. */
        return time.format(TIME_FORMATTER);
    }

    /*
     * 필수 날짜 값을 YYYY-MM-DD 문자열로 변환한다.
     *
     * @param date 문자열로 변환할 날짜
     * @return YYYY-MM-DD 형식의 날짜 문자열
     * @throws DateTimeException 필수 날짜 값이 없는 경우
     */
    public static String formatDate(LocalDate date) {
        /* 조회 날짜는 요청에서 검증되므로 null이면 응답 조립 자체가 잘못된 상태다. */
        if (date == null) {
            throw new DateTimeException("회의실 API 응답의 날짜는 null일 수 없습니다.");
        }

        /* 검증된 날짜를 공통 포맷터로 변환한다. */
        return date.format(DATE_FORMATTER);
    }
}
