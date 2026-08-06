package com.module06.backend.cap.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    D(회의) 소유 meeting 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티(project의
    TeamReferenceEntity와 동일 패턴). 쓰기 금지 — @Immutable로 dirty checking 자체를 막는다.
    D의 capture_session 애그리거트가 아직 없어도, 이미 V1에 존재하는 meeting 테이블로
    "회의 존재/companyId/hostMemberId" 검증은 지금 바로 가능하다.

    ⚠️ 이름에 Cap 접두어를 두는 이유: meetingroom 도메인도 같은 meeting 테이블을 read-model로
    매핑하는 MeetingReferenceEntity를 가진다. 클래스 단순명이 겹치면 JPA 엔티티명이 충돌해
    (Duplicate entity mapping) 스프링 컨텍스트가 통째로 죽는다. 도메인별 read-model은 각자
    소유하되 이름만 도메인 프리픽스로 분리한다.
*/
@Entity(name = "CapMeetingReference")
@Table(name = "meeting")
@Immutable
@Getter
@NoArgsConstructor
public class CapMeetingReferenceEntity {

    // 같은 meeting 테이블을 매핑하는 다른 엔티티(meeting 도메인 write 모델, meetingroom read 모델)와
    // id 생성 전략이 어긋나면, create-drop 병합 DDL에서 id 컬럼의 AUTO_INCREMENT가 빠져
    // 회의 INSERT가 "NULL not allowed for column ID"로 깨진다. IDENTITY로 정렬한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "host_member_id")
    private Long hostMemberId;

    // meeting.status ENUM('SCHEDULED','IN_PROGRESS','DONE')을 문자열로 읽는다 — "진행 중 캡처" 판정에
    // 회의가 IN_PROGRESS인지만 확인하면 되므로, 회의 도메인의 MeetingStatus enum에 의존하지 않고
    // read-model 안에서 문자열로 다룬다(파생 쿼리 findByIdInAndStatus로 필터).
    @Column(name = "status")
    private String status;
}
