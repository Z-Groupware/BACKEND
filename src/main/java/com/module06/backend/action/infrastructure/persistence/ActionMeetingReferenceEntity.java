package com.module06.backend.action.infrastructure.persistence;

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
    meeting(D, 모성진) 소유 meeting 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    존재 이유: 인수인계(E)의 ActionReassignPort.HandoverableAction이 sourceMeetingTitle을
    요구하는데, D의 엔티티를 직접 참조하면 0절 1항 위반이라 team/project 참조 엔티티와
    동일 패턴으로 신설(2026-08-06, action BC 실로직 착수 슬라이스). 쓰기 금지.
    meetingroom 도메인에 이미 동명 엔티티(meetingroom.infrastructure.persistence.entity.
    MeetingReferenceEntity)가 있어 Hibernate 엔티티명 충돌 방지 위해 Action 접두어로 구분.

    연결된 클래스
    - ActionJpaEntity                  : source_meeting_id 조인의 반대편
    - SpringDataActionRepository       : findHandoverablePersonalActions에서 조인
    - ActionMeetingReferenceRepository : ActionReassignAdapter의 배치조회
*/
@Entity
@Table(name = "meeting")
@Immutable
@Getter
@NoArgsConstructor
public class ActionMeetingReferenceEntity {

    // 회의 쓰기 엔티티(meeting 모듈)와 동일한 IDENTITY 전략 — 다르면 Hibernate가 같은 테이블에
    // 매핑된 두 엔티티의 식별자 생성 메타데이터를 혼동해 실제 insert가 깨진다(meetingroom의
    // 동명 참조 엔티티에도 동일하게 적용된 패턴, 2026-08-06 테스트로 확인).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;
}
