package com.module06.backend.meetingroom.infrastructure.persistence;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * meeting_room 테이블을 읽기 위한 JPA 영속성 엔티티다.
 *
 * ROOM-01에 필요한 컬럼만 명시적으로 매핑하고 다른 도메인 엔티티와 연관관계를 만들지 않는다.
 * company_id는 회사 엔티티 대신 식별자 값으로만 유지해 도메인 사이의 직접 의존을 방지한다.
 * 스키마 생성과 변경은 Flyway가 담당하므로 이 엔티티는 기존 테이블을 검증하고 조회하는 역할만 한다.
 */
@Entity
@Table(name = "meeting_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRoomJpaEntity {

    /* 데이터베이스가 자동으로 생성하는 회의실 기본 키다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 회의실이 소속된 회사의 식별자다. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /* 사용자 화면에 표시할 회의실 이름이다. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /* 건물과 호수 등 회의실의 위치이며 등록되지 않을 수 있다. */
    @Column(name = "location", length = 150)
    private String location;

    /* 회의실의 최대 수용 인원이다. */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    /* 회의실을 이용할 수 있는 하루 중 시작 시각이다. */
    @Column(name = "available_from", nullable = false)
    private LocalTime availableFrom;

    /* 회의실 이용을 종료해야 하는 하루 중 종료 시각이다. */
    @Column(name = "available_to", nullable = false)
    private LocalTime availableTo;

    /* 소프트 삭제에 사용하는 비활성화 시각이며 활성 회의실은 null이다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /*
     * 테스트 데이터와 이후 회의실 저장 기능에서 영속성 엔티티를 생성할 수 있게 한다.
     * 식별자가 null이면 데이터베이스가 새 식별자를 생성한다.
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
    MeetingRoomJpaEntity(
            Long id,
            Long companyId,
            String name,
            String location,
            int capacity,
            LocalTime availableFrom,
            LocalTime availableTo,
            LocalDateTime deletedAt
    ) {
        /* 전달받은 값을 meeting_room 테이블의 각 컬럼과 대응하는 필드에 저장한다. */
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
        this.deletedAt = deletedAt;
    }
}
