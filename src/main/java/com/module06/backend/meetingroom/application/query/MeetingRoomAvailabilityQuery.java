package com.module06.backend.meetingroom.application.query;

import java.time.LocalDate;

/*
 * ROOM-02 회의실 예약 현황 조회의 입력값을 담는 애플리케이션 조회 조건이다.
 *
 * companyId와 memberId는 Access Token principal에서만 채워지며 요청 파라미터로 받지 않는다.
 * 그래서 이 조회 조건을 만들 수 있는 범위 자체가 요청자의 회사와 본인으로 한정된다.
 *
 * @param companyId 인증된 요청자의 회사 식별자
 * @param memberId 인증된 요청자의 구성원 식별자, 회의 제목 열람 권한 판단에 사용한다
 * @param date 조회할 날짜
 * @param meetingRoomId 특정 회의실만 조회할 때의 식별자, 전체 조회는 null
 */
public record MeetingRoomAvailabilityQuery(
        Long companyId,
        Long memberId,
        LocalDate date,
        Long meetingRoomId
) {

    /*
     * 조회 날짜가 없는 조회 조건이 만들어지지 않도록 생성 시점에 검증한다.
     *
     * @throws IllegalArgumentException 조회 날짜가 없는 경우
     */
    public MeetingRoomAvailabilityQuery {
        /* 날짜가 없으면 슬롯 범위를 계산할 수 없으므로 잘못된 입력으로 처리한다(공통 핸들러가 400 Z-001로 변환한다). */
        if (date == null) {
            throw new IllegalArgumentException("회의실 예약 현황 조회 날짜는 필수입니다.");
        }
    }

    /*
     * 특정 회의실만 조회하는 요청인지 판단한다.
     *
     * @return 회의실 식별자가 지정되었으면 true, 전체 조회면 false
     */
    public boolean hasMeetingRoomFilter() {
        /* 회의실 식별자의 존재 여부가 곧 전체 조회와 단건 조회를 가르는 기준이다. */
        return meetingRoomId != null;
    }
}
