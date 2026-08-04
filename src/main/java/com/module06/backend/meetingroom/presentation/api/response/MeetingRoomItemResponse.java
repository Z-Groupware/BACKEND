package com.module06.backend.meetingroom.presentation.api.response;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;

/*
 * ROOM-01 응답의 회의실 항목 하나를 표현하는 프레젠테이션 DTO다.
 *
 * 이용 가능 시각은 프론트엔드 계약에 맞춰 HH:mm 문자열로 변환하며,
 * 위치가 등록되지 않은 회의실은 location을 null로 반환한다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 * @param location 회의실 위치
 * @param capacity 최대 수용 인원
 * @param availableFrom 이용 가능 시작 시각
 * @param availableTo 이용 가능 종료 시각
 */
public record MeetingRoomItemResponse(
        Long meetingRoomId,
        String name,
        String location,
        int capacity,
        String availableFrom,
        String availableTo
) {

    /* API 시간 계약을 한 곳에서 유지하기 위한 HH:mm 포맷터다. */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /*
     * 애플리케이션 조회 결과를 외부 API 응답 항목으로 변환한다.
     *
     * @param summary 변환할 회의실 조회 결과
     * @return HH:mm 형식의 이용 가능 시각을 포함한 응답 항목
     */
    public static MeetingRoomItemResponse from(MeetingRoomSummary summary) {
        /* 도메인 시간 타입을 API 계약의 문자열 표현으로 변환해 응답을 생성한다. */
        return new MeetingRoomItemResponse(
                summary.meetingRoomId(),
                summary.name(),
                summary.location(),
                summary.capacity(),
                formatTime(summary.availableFrom()),
                formatTime(summary.availableTo())
        );
    }

    /*
     * 필수 이용 가능 시각을 HH:mm 문자열로 변환한다.
     *
     * @param time 문자열로 변환할 시각
     * @return HH:mm 형식의 시각 문자열
     * @throws DateTimeException 필수 시각 값이 없거나 형식 변환에 실패한 경우
     */
    private static String formatTime(LocalTime time) {
        /* DB의 NOT NULL 시간 값을 API 명세에서 약속한 두 자리 시와 분으로 출력한다. */
        if (time == null) {
            /* 잘못된 영속성 값이 정상 응답으로 위장하지 않도록 명확한 변환 오류를 발생시킨다. */
            throw new DateTimeException("회의실 이용 가능 시각은 null일 수 없습니다.");
        }

        /* 검증된 시각을 공통 포맷터로 변환한다. */
        return time.format(TIME_FORMATTER);
    }
}
