package com.module06.backend.project.infrastructure.persistence;

import com.module06.backend.project.domain.repository.ProjectRepository;

/* comment.
    domain의 ProjectRepository 계약을 JPA로 구현하는 어댑터(의존성 역전의 실행 지점).
    책임 두 가지 — Spring Data 호출 위임, 그리고 ProjectJpaEntity ↔ Project 변환.
    JPA 예외를 그대로 위로 흘리지 않고 도메인이 이해하는 형태로 바꿔서 돌려준다.

    연결된 클래스
    - ProjectRepository           : 구현하는 도메인 계약
    - SpringDataProjectRepository : 실제 쿼리 위임 대상
    - ProjectJpaEntity            : 변환 대상 엔티티
    - Project                     : 변환 결과 도메인 모델
*/
public class ProjectPersistenceAdapter implements ProjectRepository {
}
