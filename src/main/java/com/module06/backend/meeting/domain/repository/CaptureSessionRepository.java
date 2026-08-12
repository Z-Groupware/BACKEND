package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.Meeting;

/*
 * CAP-01의 회의 잠금·시작 상태 저장과 캡처 세션 저장을 제공하는 도메인 저장소 계약이다.
 */
public interface CaptureSessionRepository {

    /* B 도메인 조회 전에 회사 범위 회의와 예약 참석자를 잠금 없이 미리 읽는다. */
    Optional<Meeting> findMeeting(Long companyId, Long meetingId);

    /* 회사 범위의 회의를 잠그고 host·상태·예약 참석자 명단을 포함해 조회한다. */
    Optional<Meeting> findMeetingForStart(Long companyId, Long meetingId);

    /* 회의당 하나인 기존 캡처 세션을 조회해 CAP-01 재호출을 멱등 처리한다. */
    Optional<CaptureSession> findByMeetingId(Long meetingId);

    /* 녹음 시작과 함께 IN_PROGRESS로 전이된 회의 상태를 같은 트랜잭션에 저장한다. */
    Meeting saveMeetingState(Meeting meeting);

    /* 신규 캡처 세션을 저장하고 데이터베이스 식별자가 반영된 애그리거트를 반환한다. */
    CaptureSession save(CaptureSession captureSession);
}
