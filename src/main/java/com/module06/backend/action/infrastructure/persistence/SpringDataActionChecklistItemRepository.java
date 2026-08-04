package com.module06.backend.action.infrastructure.persistence;

/* comment.
    action_checklist_item 테이블용 Spring Data JPA 인터페이스. 구현 시 JpaRepository를 상속한다.
    도메인 계층은 이 인터페이스를 모른다 — 어댑터만 안다.
    action_id 기준 목록 조회(액션 상세에 인라인으로 실릴 때)와 sort_order 정렬이 주 쿼리다.

    연결된 클래스
    - ActionChecklistItemJpaEntity          : 다루는 엔티티
    - ActionChecklistItemPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataActionChecklistItemRepository {
}
