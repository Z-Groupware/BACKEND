package com.module06.backend.action.infrastructure.persistence;

import com.module06.backend.action.domain.repository.ActionRepository;

/* comment.
    domain의 ActionRepository 계약을 JPA로 구현하는 어댑터(의존성 역전의 실행 지점).
    책임 두 가지 — Spring Data 호출 위임, 그리고 ActionJpaEntity ↔ Action 변환.
    JPA 예외를 그대로 위로 흘리지 않고 도메인이 이해하는 형태로 바꿔서 돌려준다.

    연결된 클래스
    - ActionRepository           : 구현하는 도메인 계약
    - SpringDataActionRepository : 실제 쿼리 위임 대상
    - ActionJpaEntity            : 변환 대상 엔티티
    - Action                     : 변환 결과 도메인 모델
*/
public class ActionPersistenceAdapter implements ActionRepository {
}
