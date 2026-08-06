package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import com.module06.backend.project.domain.model.ProjectStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    project(C, 같은 도메인이지만 다른 애그리거트) 소유 project 테이블을 읽기 전용으로 조인하기
    위한 참조 엔티티. 같은 C 도메인 내부라 해도 애그리거트 경계를 넘는 직접 참조는 피하고
    참조 전용으로 둔다(project 쪽에서 MemberReferenceEntity·TeamReferenceEntity를 두는 것과 동일 원칙).
    쓰기 금지.
    지금 쓰는 필드는 status(완료된 프로젝트 제외 필터, 2026-08-06 종준 PO 확인), tag
    (ActionReassignPort.HandoverableAction.projectTag 표시용), dueDate(AI가 기한을 비워
    보낸 액션의 마감일 기본값 — 결정로그 25번, V2.6.4) 세 개다. 첨부파일 등 나머지 표시
    필드는 GetActionDetailUseCase 착수 시 추가.

    연결된 클래스
    - ActionJpaEntity              : project_id 조인의 반대편
    - SpringDataActionRepository   : findHandoverablePersonalActions에서 조인
    - SpringDataProjectReferenceRepository : ActionReassignAdapter의 projectTag 배치조회,
                                             ActionDistributionService의 마감일 기본값 배치조회
*/
@Entity
@Table(name = "project")
@Immutable
@Getter
@NoArgsConstructor
public class ProjectReferenceEntity {

    // project 쓰기 엔티티(ProjectJpaEntity)와 동일한 IDENTITY 전략 — 안 맞추면 Hibernate가
    // 같은 테이블에 매핑된 두 엔티티의 식별자 생성 메타데이터를 혼동한다(2026-08-06 테스트로 확인).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "tag")
    private String tag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProjectStatus status;

    // 액션 마감일의 원천. AI가 dueDate를 비워 보내면 이 값으로 채우고 due_date_defaulted=true로 남긴다.
    @Column(name = "due_date")
    private LocalDate dueDate;
}
