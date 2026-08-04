package com.module06.backend.action.infrastructure.persistence;

/* comment.
    action 테이블용 Spring Data JPA 인터페이스. 구현 시 JpaRepository를 상속한다.
    도메인 계층은 이 인터페이스를 모른다 — 어댑터만 안다.
    개인 액션 목록(assignee 스코프)·팀 액션 목록(teamId 스코프)·타임라인(parentActionId 기준)이
    모두 이 인터페이스의 쿼리 메서드로 갈린다 — N+1이 터지기 쉬운 지점이라 fetch join / projection 검토 필요.

    연결된 클래스
    - ActionJpaEntity          : 다루는 엔티티
    - ActionPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataActionRepository {
}
