package com.module06.backend.domain.project.domain.repository;

/* comment.
    프로젝트 저장소 계약. domain 계층이 선언하고 infrastructure가 구현한다(의존성 역전).
    JPA·쿼리 같은 기술 세부사항은 이 인터페이스에 드러나지 않는다.

    연결된 클래스
    - Project                     : 다루는 도메인 모델
    - ProjectPersistenceAdapter   : 구현체 (infrastructure.persistence, 미생성)
    - SpringDataProjectRepository : 어댑터가 위임하는 Spring Data 인터페이스 (미생성)
    - application.service.*       : 이 계약을 주입받는 유스케이스 구현체들 (미생성)
*/
public interface ProjectRepository {
}
