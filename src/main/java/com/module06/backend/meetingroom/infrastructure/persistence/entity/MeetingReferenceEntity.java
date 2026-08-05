package com.module06.backend.meetingroom.infrastructure.persistence.entity;

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
 * 회의 애그리거트의 주인은 회의 기능이며, 회의실 현황 조회는 슬롯을 점유한 회의의 제목과 회사 스코프만 필요하다.
 * 그래서 회의 엔티티를 통째로 끌어오지 않고 필요한 세 컬럼만 매핑하고 @Immutable로 쓰기 자체를 막는다.
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

    /*
     * 테스트에서 조회 대상 회의 데이터를 준비할 수 있게 한다.
     * 운영 경로에서 이 엔티티로 회의를 저장하지 않으며, 회의 생성은 회의 기능이 담당한다.
     *
     * @param id 회의 식별자
     * @param companyId 회의가 속한 회사 식별자
     * @param title 회의 제목
     */
    public MeetingReferenceEntity(Long id, Long companyId, String title) {
        /* 읽기 전용 참조에 필요한 세 값만 저장한다. */
        this.id = id;
        this.companyId = companyId;
        this.title = title;
    }
}
