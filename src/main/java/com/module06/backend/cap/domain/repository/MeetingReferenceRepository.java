package com.module06.backend.cap.domain.repository;

import java.util.Optional;

/* comment.
    D(회의) 소유 meeting/meeting_attendee 테이블 읽기 전용 조회 계약(project의
    TeamReferenceRepository와 동일 패턴). 회의 도메인(D)이 아직 없어도 이미 V1에 존재하는
    테이블이라 "회의 존재/참석자 여부" 검증은 지금 바로 가능하다.
*/
public interface MeetingReferenceRepository {

    /** 회의가 실제로 존재하는지 */
    boolean existsById(Long meetingId);

    /** 이 사람이 회의 참석자 명단에 있는지 */
    boolean isAttendee(Long meetingId, Long memberId);

    /** 이 사람이 회의 담당자(Host)인지 — 수동 녹음 업로드(CAP-10)는 Host만 가능. */
    boolean isHost(Long meetingId, Long memberId);

    /** S3 키 경로(org-{companyId}/meeting-{meetingId}/...) 조립용. 회의가 없으면 empty. */
    Optional<Long> findCompanyId(Long meetingId);

    /** 이 회의의 전체 참석자 수(CAP-13 SSE participant 이벤트의 totalCount). */
    int countAttendees(Long meetingId);
}
