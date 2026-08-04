package com.module06.backend.action.infrastructure.persistence;

/* comment.
    project(C, 같은 도메인이지만 다른 애그리거트) 소유 project·project_attachment 테이블을
    읽기 전용으로 조인하기 위한 참조 엔티티. 존재 이유: 개인 액션 상세의 프로젝트 태그 표시,
    팀 액션 상세(FR-AC-06)의 첨부파일 인라인 포함에 필요하다.
    같은 C 도메인 내부라 해도 애그리거트 경계를 넘는 직접 참조는 피하고 참조 전용으로 둔다
    (project 쪽에서 MemberReferenceEntity·TeamReferenceEntity를 두는 것과 동일한 원칙).
    조회할 컬럼은 id·tag·첨부파일 목록(파일명·URL) 정도다. 쓰기 금지.

    연결된 클래스
    - ActionService · TeamActionService : 프로젝트 태그·첨부파일 표시
    - ActionJpaEntity                    : project_id 조인의 반대편
*/
public class ProjectReferenceEntity {
}
