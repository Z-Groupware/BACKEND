package com.module06.backend.cap.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 findById/existsById 등 기본 CRUD를 자동 구현해준다.
// ⚠️ Cap 접두어 이유: meetingroom 도메인의 동명 리포지토리와 Spring 빈 이름(단순명 기준)이 겹치면
//    BeanDefinitionOverrideException으로 컨텍스트가 죽는다. 도메인 프리픽스로 빈 이름을 분리한다.
public interface SpringDataCapMeetingReferenceRepository extends JpaRepository<CapMeetingReferenceEntity, Long> {

    // 주어진 회의들 중 특정 상태(예: IN_PROGRESS)인 것만. "진행 중 캡처" 조회에서 참석 회의들을
    // 회의 상태로 좁힐 때 사용. QUERY_002(신규 @Query 금지)에 걸리지 않도록 파생 쿼리로 표현한다.
    // id만 필요하므로 닫힌 프로젝션으로 id 컬럼만 SELECT한다(companyId·hostMemberId·status 로드 방지).
    List<MeetingIdView> findByIdInAndStatus(Collection<Long> ids, String status);

    /** id 컬럼만 뽑는 닫힌 프로젝션 — 진행 중 회의 id 목록만 필요할 때 사용. */
    interface MeetingIdView {
        Long getId();
    }
}
