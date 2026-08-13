package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.CaptureUploadState;

import java.util.Optional;

/* comment.
    "진행 중 캡처 조회"(CAP-09)용 읽기 계약. 회의(meeting)·참석자(meeting_attendee)·캡처 상태
    (capture_upload_state) 세 테이블에 걸친 조회라 단일 애그리거트 영속성 계약(CaptureUploadStateRepository)이
    아니라 별도 쿼리 계약으로 둔다(project의 read-model 조회 계약과 동일 패턴).

    반환은 CaptureUploadState 애그리거트를 재사용한다 — 응답에 필요한 segmentSeq/lastSeq/recorderPersonId/
    createdAt가 전부 이 안에 있고, canTakeover(하트비트)·elapsedMs·captureSessionId는 서비스 계층에서
    덧붙인다(하트비트는 CaptureHeartbeatPort, elapsedMs는 createdAt 기준, captureSessionId는
    CapCaptureSessionReferenceRepository — 세션 행이 없으면 null).
*/
public interface ActiveCaptureQueryRepository {

    /**
     * 이 사람이 참석 중이면서 회의가 진행 중(IN_PROGRESS)인 캡처를 하나 반환한다. 여러 개면 가장 최근에
     * 시작된 것. 없으면 empty(진행 중인 캡처 없음).
     */
    Optional<CaptureUploadState> findActiveCaptureForMember(Long memberId);
}
