package com.module06.backend.meetingroom.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

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
 * meeting 테이블에서 예약 현황 표시에 필요한 값만 읽기 전용으로 조회하는 참조 엔티티다.
 *
 * 회의 애그리거트의 주인은 회의 기능이며, 회의실 기능은 예약 표시와 운영 시간 충돌 확인용 값만 필요하다.
 * 그래서 회의 엔티티를 통째로 끌어오지 않고 식별·표시·상태·예약 시간 컬럼만 매핑하고 @Immutable로 쓰기를 막는다.
 * 회의 도메인 구현이 들어오면 이 엔티티를 그 도메인의 조회 포트 호출로 대체할 수 있도록 어댑터 뒤에 숨겨 둔다.
 *
 * 연결된 클래스
 * - SpringDataMeetingReferenceRepository: 이 엔티티를 조회하는 기술 저장소
 * - MeetingRoomSlotPersistenceAdapter: 슬롯에 회의 제목을 채우고 회사 스코프를 검증한다
 */
@Entity
@Table(name = "meeting")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingReferenceEntity {

    /* 회의를 식별하며 회의 쓰기 엔티티와 동일한 IDENTITY 전략을 사용하는 기본 키다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /* 회의가 속한 회사의 식별자이며 테넌트 스코프 검증에 사용한다. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /* 예약 현황판에 노출할 회의 제목이다. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /* 예약된 회의실 식별자이며 ROOM-04 미래 예약 범위 조회에 사용한다. */
    @Column(name = "meeting_room_id", nullable = false)
    private Long meetingRoomId;

    /* 회의 생명주기 상태 문자열이며 ROOM-04는 SCHEDULED 행만 조회한다. */
    @Column(name = "status", nullable = false)
    private String status;

    /* 예약 시작 일시이며 미래 예약 판정과 운영 시작 경계 비교에 사용한다. */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /* 예약 종료 일시이며 운영 종료 경계 비교에 사용한다. */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /*
     * 테스트에서 조회 대상 회의 데이터를 준비할 수 있게 한다.
     * 운영 경로에서 이 엔티티로 회의를 저장하지 않으며, 회의 생성은 회의 기능이 담당한다.
     *
     * @param id 회의 식별자
     * @param companyId 회의가 속한 회사 식별자
     * @param title 회의 제목
     */
    public MeetingReferenceEntity(
            Long id,
            Long companyId,
            String title,
            Long meetingRoomId,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        /* 읽기 전용 참조에 필요한 회사·표시·예약 시간 값을 저장한다. */
        this.id = id;
        this.companyId = companyId;
        this.title = title;
        this.meetingRoomId = meetingRoomId;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
