package com.module06.backend.meeting.domain.repository;

import java.util.Optional;

import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.Meeting;

/*
 * CAP-02·03의 host 확인과 캡처 세션 상태 잠금·저장을 제공하는 도메인 저장소 계약이다.
 */
public interface CaptureSessionControlRepository {

    /* 회사 범위 회의의 host 정보를 참석자 목록 없이 조회한다. */
    Optional<Meeting> findMeetingForControl(Long companyId, Long meetingId);

    /* 회의당 하나인 캡처 세션 행을 쓰기 잠금으로 조회한다. */
    Optional<CaptureSession> findByMeetingIdForUpdate(Long meetingId);

    /* 상태 전이가 반영된 기존 캡처 세션을 저장한다. */
    CaptureSession save(CaptureSession captureSession);
}
