package com.module06.backend.meetingroom.domain.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Getter;

/*
 * 회의실 애그리거트 루트다.
 *
 * ROOM-01에서는 회사별 활성 회의실 목록을 조회할 때 사용하며,
 * 다른 도메인의 엔티티를 직접 참조하지 않고 회사 식별자만 값으로 보관한다.
 * deletedAt이 null이면 사용할 수 있는 회의실이고, 값이 존재하면 비활성화된 회의실이다.
 *
 * 연결된 클래스
 * - MeetingRoomRepository: 회의실 조회를 위한 도메인 저장소 계약
 * - MeetingRoomService: ROOM-01 조회 유스케이스 구현체
 * - MeetingRoomSummary: API 응답에 필요한 값만 전달하는 애플리케이션 결과 객체
 */
@Getter
public class MeetingRoom {

    /* 회의실을 식별하는 기본 키다. */
    private final Long id;

    /* 회의실이 소속된 회사를 식별하는 값이다. */
    private final Long companyId;

    /* 사용자 화면에 노출되는 회의실 이름이다. */
    private final String name;

    /* 건물과 호수 등 회의실의 물리적 위치다. */
    private final String location;

    /* 회의실이 수용할 수 있는 최대 인원이다. */
    private final int capacity;

    /* 하루 중 회의실 이용을 시작할 수 있는 시각이다. */
    private final LocalTime availableFrom;

    /* 하루 중 회의실 이용을 종료해야 하는 시각이다. */
    private final LocalTime availableTo;

    /* 회의실 비활성화 시각이며, 활성 상태에서는 null이다. */
    private final LocalDateTime deletedAt;

    /*
     * 영속성 계층에서 조회한 회의실 상태를 도메인 객체로 복원한다.
     *
     * @param id 회의실 식별자
     * @param companyId 소속 회사 식별자
     * @param name 회의실 이름
     * @param location 회의실 위치
     * @param capacity 최대 수용 인원
     * @param availableFrom 이용 가능 시작 시각
     * @param availableTo 이용 가능 종료 시각
     * @param deletedAt 비활성화 시각
     */
    public MeetingRoom(
            Long id,
            Long companyId,
            String name,
            String location,
            int capacity,
            LocalTime availableFrom,
            LocalTime availableTo,
            LocalDateTime deletedAt
    ) {
        /* 전달받은 식별자와 회의실 속성을 변경 불가능한 필드에 저장한다. */
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
        this.deletedAt = deletedAt;
    }

    /*
     * 회의실이 현재 활성 상태인지 판단한다.
     *
     * @return 비활성화 시각이 없으면 true, 있으면 false
     */
    public boolean isActive() {
        /* 소프트 삭제 시각의 존재 여부를 회의실 활성 상태로 해석한다. */
        return deletedAt == null;
    }
}
