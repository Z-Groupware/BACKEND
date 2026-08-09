package com.module06.backend.meeting.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.query.GetCaptureSessionQuery;
import com.module06.backend.meeting.application.result.CaptureSessionStateResult;
import com.module06.backend.meeting.application.usecase.GetCaptureSessionUseCase;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.repository.CaptureSessionQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;
import com.module06.backend.meeting.exception.CaptureSessionErrorCode;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * CAP-10의 회사 범위·예약 참석자 권한과 현재 캡처 세션 상태 조회를 조율하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class CaptureSessionQueryService implements GetCaptureSessionUseCase {

    /* 회사 범위 회의와 host·예약 참석자 식별자를 제공하는 D 회의 조회 저장소다. */
    private final MeetingQueryRepository meetingQueryRepository;

    /* 현재 캡처 세션을 비잠금으로 읽는 D 캡처 세션 조회 저장소다. */
    private final CaptureSessionQueryRepository captureSessionQueryRepository;

    /* 예약 참석자의 새로고침·재접속 복구를 위해 현재 세션 상태와 시간축을 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public CaptureSessionStateResult getCaptureSession(GetCaptureSessionQuery query) {
        /* Controller를 우회한 호출도 잘못된 인증·Path 값으로 저장소를 조회하지 못하게 한다. */
        validateQuery(query);

        /* companyId 조건을 함께 사용해 타 회사 회의도 존재하지 않는 회의처럼 처리한다. */
        MeetingSnapshot meeting = meetingQueryRepository
                .findMeeting(query.companyId(), query.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* 역할과 무관하게 실제 host 또는 예약 참석자만 캡처 상태를 복원할 수 있다. */
        boolean host = meeting.hostMemberId().equals(query.requesterMemberId());
        boolean attendee = meeting.attendeeMemberIds().contains(query.requesterMemberId());
        if (!host && !attendee) {
            throw new BusinessException(MeetingErrorCode.ATTENDEE_ONLY);
        }

        /* 회의는 있지만 CAP-01이 실행되지 않았다면 명세의 CS-001로 구분한다. */
        CaptureSession captureSession = captureSessionQueryRepository
                .findByMeetingId(meeting.meetingId())
                .orElseThrow(() -> new BusinessException(
                        CaptureSessionErrorCode.CAPTURE_SESSION_NOT_FOUND
                ));

        /* A 소유 녹음자·세그먼트 값을 섞지 않고 D 소유 상태와 기준 시간축만 반환한다. */
        return new CaptureSessionStateResult(
                captureSession.getId(),
                captureSession.getStatus(),
                captureSession.isPaused(),
                captureSession.getStartedAtEpochMs(),
                captureSession.getStartedAt(),
                captureSession.getPausedAt()
        );
    }

    /* 인증 회사·구성원과 Path 회의 식별자의 기본 형식을 저장소 호출 전에 검증한다. */
    private void validateQuery(GetCaptureSessionQuery query) {
        /* null 또는 양수가 아닌 식별자는 공통 입력 오류로 일관되게 반환한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L
                || query.meetingId() == null
                || query.meetingId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
