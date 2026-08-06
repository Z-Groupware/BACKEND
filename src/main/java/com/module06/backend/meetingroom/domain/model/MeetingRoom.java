package com.module06.backend.meetingroom.domain.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.Getter;

/*
 * 회의실 애그리거트 루트다.
 *
 * ROOM-01에서는 회사별 활성 회의실 목록을 조회할 때 사용하며,
 * 다른 도메인의 엔티티를 직접 참조하지 않고 회사 식별자만 값으로 보관한다.
 * deletedAt이 null이면 사용할 수 있는 회의실이고, 값이 존재하면 비활성화된 회의실이다.
 * ROOM-02에서는 자신의 이용 가능 시간을 30분 슬롯으로 분할하는 책임까지 갖는다.
 *
 * 연결된 클래스
 * - MeetingRoomRepository: 회의실 조회를 위한 도메인 저장소 계약
 * - MeetingRoomService: ROOM-01 조회 유스케이스 구현체
 * - MeetingRoomSummary: API 응답에 필요한 값만 전달하는 애플리케이션 결과 객체
 * - SlotGrid: 이용 가능 시간을 30분 슬롯으로 분할하는 도메인 규칙
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

    /* 검증이 끝난 등록 요청으로 아직 식별자가 없는 활성 회의실을 생성한다. */
    public static MeetingRoom create(
            Long companyId,
            String name,
            String location,
            int capacity,
            LocalTime availableFrom,
            LocalTime availableTo
    ) {
        /* 이름과 위치의 가장자리 공백을 제거하고 빈 위치는 미등록 값인 null로 정규화한다. */
        String normalizedLocation = location == null || location.trim().isEmpty()
                ? null
                : location.trim();

        /* 신규 회의실은 데이터베이스 식별자와 비활성화 시각이 없는 활성 상태로 시작한다. */
        return new MeetingRoom(
                null,
                companyId,
                name.trim(),
                normalizedLocation,
                capacity,
                availableFrom,
                availableTo,
                null
        );
    }

    /* 검증된 최종 속성으로 식별자와 활성 상태를 유지한 새 회의실 상태를 만든다. */
    public MeetingRoom update(
            String name,
            String location,
            int capacity,
            LocalTime availableFrom,
            LocalTime availableTo
    ) {
        /* 빈 위치는 미등록 상태인 null로 통일하고 그 외 문자열의 가장자리 공백을 제거한다. */
        String normalizedLocation = location == null || location.trim().isEmpty()
                ? null
                : location.trim();

        /* 수정은 기존 식별자·회사·비활성화 상태를 바꾸지 않고 관리 가능한 속성만 교체한다. */
        return new MeetingRoom(
                id,
                companyId,
                name.trim(),
                normalizedLocation,
                capacity,
                availableFrom,
                availableTo,
                deletedAt
        );
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

    /*
     * 이 회의실의 하루 예약 그리드를 구성하는 슬롯 시작 시각을 계산한다.
     * 이용 가능 시간 밖의 슬롯은 만들지 않으므로, ROOM-02 응답에는 예약할 수 있는 칸만 담긴다.
     *
     * @return 이용 가능 시간을 30분으로 분할한 슬롯 시작 시각 목록
     */
    public List<LocalTime> slotStartTimes() {
        /* 슬롯 길이와 분할 규칙은 회의 개설과 공유하는 기준이므로 도메인 규칙에 위임한다. */
        return SlotGrid.slotStarts(availableFrom, availableTo);
    }
}
