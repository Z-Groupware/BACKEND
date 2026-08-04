package com.module06.backend.action.infrastructure.persistence;

/* comment.
    action 테이블 JPA 매핑. 도메인 모델 Action과 1:1로 변환된다.
    매핑 대상 컬럼: id·company_id·project_id·parent_action_id·source_meeting_id·team_id·
    assignee_member_id·action_type·title·description·status·due_date·needs_review·
    confirmed_at·created_at·updated_at (V1__init_schema.sql 기준).
    다른 도메인 엔티티를 @ManyToOne으로 물지 않는다 — project_id·team_id·assignee_member_id·
    source_meeting_id는 전부 id 값으로만 둔다(0절 1항).
    스키마 주인은 Flyway이므로 ddl-auto는 validate 이상으로 올리지 않는다.
    TEAM/PERSONAL 자기참조(parent_action_id)를 이 엔티티도 그대로 반영한다 — @ManyToOne self-join이 아니라
    id 값 컬럼으로만 둔다(도메인 내부 참조라도 0절 1항과 동일한 원칙 적용).

    연결된 클래스
    - Action                      : 변환 대상 도메인 모델
    - SpringDataActionRepository  : 이 엔티티를 다루는 Spring Data 인터페이스
    - ActionPersistenceAdapter    : 도메인 ↔ 엔티티 변환 담당
    - MemberReferenceEntity · TeamReferenceEntity · ProjectReferenceEntity : 조인 시 함께 읽는 참조 엔티티
*/
public class ActionJpaEntity {
}
