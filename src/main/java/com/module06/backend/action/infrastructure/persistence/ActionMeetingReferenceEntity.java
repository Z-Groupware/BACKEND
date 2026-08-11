package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDateTime;

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

    teamId·relatedActionId는 AI 분배(ActionDistributionPort)가 쓴다. 분배 계약이 이 두 값을
    주지 않기 때문에 C가 회의에서 유도해야 한다 — TEAM 액션의 대상 팀은 meeting.team_id,
    PERSONAL 액션의 상위 팀 액션은 meeting.related_action_id(V3.1.1)다(결정로그 25번).
    둘 다 NULL 가능하다: team_id는 OWNER 개설 회의면 없고, related_action_id는 프로젝트
    회의(팀 액션을 만들어내는 쪽)면 없다.

    연결된 클래스
    - ActionJpaEntity                  : source_meeting_id 조인의 반대편
    - SpringDataActionRepository       : findHandoverablePersonalActions에서 조인
    - ActionMeetingReferenceRepository : ActionReassignAdapter의 배치조회,
                                         ActionDistributionService의 teamId·상위액션 배치조회
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

    // OWNER 개설 회의는 NULL — 그 회의에서 TEAM 액션을 만들려 하면 대상 팀을 특정할 수 없다.
    @Column(name = "team_id")
    private Long teamId;

    // 이 회의가 다루는 상위 팀 액션(V3.1.1). 프로젝트 회의(팀 액션을 낳는 쪽)는 NULL.
    @Column(name = "related_action_id")
    private Long relatedActionId;

    // 2026-08-11 — 개인 액션 상세의 "출처 회의 일시" 표시용.
    @Column(name = "start_at")
    private LocalDateTime startAt;
}
