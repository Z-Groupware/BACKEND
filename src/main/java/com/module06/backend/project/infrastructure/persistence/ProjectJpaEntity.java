package com.module06.backend.project.infrastructure.persistence;

/* comment.
    project 테이블 JPA 매핑. 도메인 모델 Project와 1:1로 변환된다.
    매핑 대상 컬럼: id·company_id·tag·name·description·color·status·due_date·created_by·
    deleted_at·created_at·updated_at (V1__init_schema.sql 기준).
    다른 도메인 엔티티를 @ManyToOne으로 물지 않는다 — company_id·created_by는 id 값으로만 둔다(0절 1항).
    스키마 주인은 Flyway이므로 ddl-auto는 validate 이상으로 올리지 않는다.

    미결: 지정 부서 목록(project_team 조인 테이블)을 @ElementCollection으로 이 엔티티에 붙일지,
    별도 엔티티로 뺄지 결정 필요. project_team은 (project_id, team_id) 복합 PK이고 C 소유다.

    연결된 클래스
    - Project                     : 변환 대상 도메인 모델
    - SpringDataProjectRepository  : 이 엔티티를 다루는 Spring Data 인터페이스
    - ProjectPersistenceAdapter    : 도메인 ↔ 엔티티 변환 담당
    - TeamReferenceEntity          : 부서명 조인 시 함께 읽는 참조 엔티티
*/
public class ProjectJpaEntity {
}
