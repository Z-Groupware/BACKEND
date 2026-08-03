package com.module06.backend.project.infrastructure.persistence;

/* comment.
    project 테이블용 Spring Data JPA 인터페이스. 구현 시 JpaRepository를 상속한다.
    도메인 계층은 이 인터페이스를 모른다 — 어댑터만 안다.
    목록·타임라인 조회는 N+1이 터지기 쉬운 지점이라 fetch join / projection을 검토해야 한다.

    연결된 클래스
    - ProjectJpaEntity          : 다루는 엔티티
    - ProjectPersistenceAdapter : 이 인터페이스에 위임하는 어댑터
*/
public interface SpringDataProjectRepository {
}
