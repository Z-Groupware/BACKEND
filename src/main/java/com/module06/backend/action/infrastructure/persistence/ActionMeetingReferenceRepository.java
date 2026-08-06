package com.module06.backend.action.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
    MeetingReferenceEntity 배치조회 전용. ActionReassignAdapter가 인수인계 조회 결과를
    E에게 넘기기 전 sourceMeetingTitle 표시값을 채우는 데 쓴다(N+1 방지 위해 findAllById로 일괄조회).
    이름을 SpringDataMeetingReferenceRepository로 두지 않은 이유 — meetingroom 도메인에 이미
    동명 인터페이스가 있어 Spring Data 빈 이름이 충돌한다(단순 클래스명 기준 등록이라 패키지가
    달라도 겹침). Action이라는 접두어로 구분.
*/
public interface ActionMeetingReferenceRepository extends JpaRepository<ActionMeetingReferenceEntity, Long> {
}
