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
 * @param date 조회할 주의 기준일, 생략하면 서비스가 KST 오늘을 사용한다
 * @param meetingRoomId 주간 현황을 조회할 필수 회의실 식별자
 */
public record MeetingRoomAvailabilityQuery(
        Long companyId,
        Long memberId,
        LocalDate date,
        Long meetingRoomId
) {

    /*
     * 회의실 식별자가 없는 조회 조건이 만들어지지 않도록 생성 시점에 검증한다.
     *
     * @throws IllegalArgumentException 회의실 식별자가 없는 경우
     */
    public MeetingRoomAvailabilityQuery {
        /* 단일 회의실 주간 조회 계약이므로 회의실 식별자는 반드시 양수여야 한다. */
        if (meetingRoomId == null || meetingRoomId <= 0) {
            throw new IllegalArgumentException("회의실 예약 현황 조회에는 회의실 식별자가 필요합니다.");
        }
    }
}
