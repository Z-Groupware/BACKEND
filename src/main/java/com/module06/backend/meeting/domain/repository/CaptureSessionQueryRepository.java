package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.CaptureSession;

/*
 * CAP-10이 회의별 캡처 세션을 잠금 없이 읽는 도메인 저장소 계약이다.
 */
public interface CaptureSessionQueryRepository {

    /* 회의당 하나인 현재 캡처 세션을 상태 변경용 쓰기 잠금 없이 조회한다. */
    Optional<CaptureSession> findByMeetingId(Long meetingId);
}
