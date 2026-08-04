package com.module06.backend.action.domain.repository;

/* comment.
    체크리스트 항목 저장소 계약. domain 계층이 선언하고 infrastructure가 구현한다(의존성 역전).
    JPA·쿼리 같은 기술 세부사항은 이 인터페이스에 드러나지 않는다.

    연결된 클래스
    - ActionChecklistItem                  : 다루는 도메인 모델
    - ActionChecklistItemPersistenceAdapter: 구현체 (infrastructure.persistence)
    - SpringDataActionChecklistItemRepository : 어댑터가 위임하는 Spring Data 인터페이스
    - application.service.ChecklistItemService: 이 계약을 주입받는 유스케이스 구현체
*/
public interface ActionChecklistItemRepository {
}
