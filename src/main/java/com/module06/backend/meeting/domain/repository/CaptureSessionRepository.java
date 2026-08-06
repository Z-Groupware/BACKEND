package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.Meeting;

/*
 * CAP-01의 회의 잠금 조회와 캡처 세션 저장을 제공하는 도메인 저장소 계약이다.
 */
public interface CaptureSessionRepository {

    /* 회사 범위의 회의를 잠그고 host·상태·예약 참석자 명단을 포함해 조회한다. */
    Optional<Meeting> findMeetingForStart(Long companyId, Long meetingId);

    /* 해당 회의에 이미 생성된 캡처 세션이 있는지 확인한다. */
    boolean existsByMeetingId(Long meetingId);

    /* 신규 캡처 세션을 저장하고 데이터베이스 식별자가 반영된 애그리거트를 반환한다. */
    CaptureSession save(CaptureSession captureSession);
}
