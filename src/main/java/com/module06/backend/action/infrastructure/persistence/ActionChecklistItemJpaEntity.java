package com.module06.backend.action.infrastructure.persistence;

/* comment.
    action_checklist_item 테이블 JPA 매핑. 도메인 모델 ActionChecklistItem과 1:1로 변환된다.
    매핑 대상 컬럼: id·action_id·content·is_done·sort_order·created_at·updated_at
    (V1__init_schema.sql 기준). action_id는 id 값으로만 두고 @ManyToOne으로 물지 않는다(0절 1항).

    연결된 클래스
    - ActionChecklistItem                     : 변환 대상 도메인 모델
    - SpringDataActionChecklistItemRepository : 이 엔티티를 다루는 Spring Data 인터페이스
    - ActionChecklistItemPersistenceAdapter   : 도메인 ↔ 엔티티 변환 담당
    - ActionJpaEntity                         : action_id 조인의 반대편
*/
public class ActionChecklistItemJpaEntity {
}
