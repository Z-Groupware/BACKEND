package com.module06.backend.project.domain.model;

/* comment.
    프로젝트 애그리거트 루트. 태그·이름·기획(description)·색상·마감일·상태·지정 부서를 보유한다.
    생성은 OWNER만, 조회는 전 구성원 공개. 삭제(D)는 이번 스프린트 스코프 아웃(CRU만).
    지정 부서·담당자는 다른 도메인 엔티티를 참조하지 않고 id 값만 가진다(0절 절대규칙 1항).

    연결된 클래스
    - ProjectStatus            : 상태 값(TODO/IN_PROGRESS/DONE)
    - ProjectAttachment        : 이 프로젝트에 달린 첨부파일 메타데이터
    - ProjectTagImmutablePolicy: 태그 변경 금지 규칙(FR-PJ-04)
    - ProjectRepository        : 저장소 계약
    - ProjectJpaEntity         : 영속화 매핑 (infrastructure.persistence)
*/
public class Project {
}
