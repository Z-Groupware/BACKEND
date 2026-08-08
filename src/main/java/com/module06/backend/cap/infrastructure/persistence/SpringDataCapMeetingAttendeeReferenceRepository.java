package com.module06.backend.cap.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// 실제 Spring Data JPA 리포지토리. JpaRepository가 existsById(복합키) 등 기본 CRUD를 자동 구현해준다.
// ⚠️ Cap 접두어 이유: meetingroom 도메인의 동명 리포지토리와 빈 이름이 겹치는 것을 피한다.
public interface SpringDataCapMeetingAttendeeReferenceRepository
        extends JpaRepository<CapMeetingAttendeeReferenceEntity, MeetingAttendeeId> {

    // 이 사람이 참석자로 들어가 있는 회의들. 복합키 id의 memberId 속성으로 파생(id.memberId → IdMemberId).
    // "진행 중 캡처" 조회의 시작점 — 토큰 사용자가 참석 중인 회의 후보를 뽑는다.
    List<CapMeetingAttendeeReferenceEntity> findByIdMemberId(Long memberId);

    // 이 회의의 전체 참석자 수(CAP-13 SSE participant 이벤트의 totalCount). 복합키 id의 meetingId로 파생.
    int countByIdMeetingId(Long meetingId);
}
