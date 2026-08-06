package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.PauseCaptureSessionCommand;
import com.module06.backend.meeting.application.command.ResumeCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionPauseResult;
import com.module06.backend.meeting.application.result.CaptureSessionResumeResult;
import com.module06.backend.meeting.application.usecase.PauseCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.ResumeCaptureSessionUseCase;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.repository.CaptureSessionControlRepository;
import com.module06.backend.meeting.exception.CaptureSessionErrorCode;

/*
 * CAP-02·03 캡처 세션 상태 전이의 회사 범위·host·현재 상태 검증을 조율하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class CaptureSessionLifecycleService implements
        PauseCaptureSessionUseCase,
        ResumeCaptureSessionUseCase {

    /* 회의 host 조회와 캡처 세션 잠금·저장을 수행하는 D 도메인 저장소다. */
    private final CaptureSessionControlRepository captureSessionControlRepository;

    /* 상태 전이 시각과 updatedAt을 동일한 KST 시각으로 기록하기 위한 서버 시계다. */
    private final Clock clock;

    /* host 요청으로 ACTIVE 캡처 세션을 잠그고 PAUSED 상태로 전이한다. */
    @Override
    @Transactional
    public CaptureSessionPauseResult pauseCaptureSession(PauseCaptureSessionCommand command) {
        /* Controller를 우회한 호출도 잘못된 인증·Path 값으로 저장소를 조회하지 못하게 한다. */
        validateCommand(
                command == null ? null : command.companyId(),
                command == null ? null : command.requesterMemberId(),
                command == null ? null : command.meetingId()
        );

        /* 타 회사 또는 미존재 회의는 세션 부재와 같은 CS-001로 숨긴다. */
        Meeting meeting = captureSessionControlRepository
                .findMeetingForControl(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(
                        CaptureSessionErrorCode.CAPTURE_SESSION_NOT_FOUND
                ));

        /* 회사 역할과 무관하게 실제 회의 개설자만 캡처 상태를 제어할 수 있다. */
        if (!meeting.isHost(command.requesterMemberId())) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_HOST_ONLY);
        }

        /* 같은 세션의 동시 일시정지·종료 전이를 직렬화하기 위해 세션 행을 잠금 조회한다. */
        CaptureSession captureSession = captureSessionControlRepository
                .findByMeetingIdForUpdate(meeting.getId())
                .orElseThrow(() -> new BusinessException(
                        CaptureSessionErrorCode.CAPTURE_SESSION_NOT_FOUND
                ));

        /* 멱등 성공으로 숨기지 않고 이미 일시정지된 상태를 명세의 CS-004로 구분한다. */
        if (captureSession.getStatus() == CaptureSessionStatus.PAUSED) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_PAUSED);
        }

        /* 종료된 세션을 PAUSED로 되돌리는 상태 역행은 CS-006으로 차단한다. */
        if (captureSession.getStatus() == CaptureSessionStatus.ENDED) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_ENDED);
        }

        /* 한 요청 안의 pausedAt과 updatedAt이 같도록 현재 KST 시각을 한 번만 읽어 전이한다. */
        CaptureSession pausedSession = captureSession.pause(LocalDateTime.now(clock));
        CaptureSession savedSession = captureSessionControlRepository.save(pausedSession);

        /* 프레젠테이션 계층에는 세션 식별자와 일시정지 상태·시각만 전달한다. */
        return new CaptureSessionPauseResult(
                savedSession.getId(),
                savedSession.getStatus(),
                savedSession.isPaused(),
                savedSession.getPausedAt()
        );
    }

    /* host 요청으로 PAUSED 캡처 세션을 잠그고 ACTIVE 상태로 전이한다. */
    @Override
    @Transactional
    public CaptureSessionResumeResult resumeCaptureSession(ResumeCaptureSessionCommand command) {
        /* Controller를 우회한 호출도 잘못된 인증·Path 값으로 저장소를 조회하지 못하게 한다. */
        validateCommand(
                command == null ? null : command.companyId(),
                command == null ? null : command.requesterMemberId(),
                command == null ? null : command.meetingId()
        );

        /* 타 회사 또는 미존재 회의는 세션 부재와 같은 CS-001로 숨긴다. */
        Meeting meeting = captureSessionControlRepository
                .findMeetingForControl(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(
                        CaptureSessionErrorCode.CAPTURE_SESSION_NOT_FOUND
                ));

        /* 회사 역할과 무관하게 실제 회의 개설자만 캡처 상태를 제어할 수 있다. */
        if (!meeting.isHost(command.requesterMemberId())) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_HOST_ONLY);
        }

        /* 같은 세션의 동시 재개·종료 전이를 직렬화하기 위해 세션 행을 잠금 조회한다. */
        CaptureSession captureSession = captureSessionControlRepository
                .findByMeetingIdForUpdate(meeting.getId())
                .orElseThrow(() -> new BusinessException(
                        CaptureSessionErrorCode.CAPTURE_SESSION_NOT_FOUND
                ));

        /* 이미 활성 상태인 세션의 재개 요청은 명세의 CS-005로 구분한다. */
        if (captureSession.getStatus() == CaptureSessionStatus.ACTIVE) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_ACTIVE);
        }

        /* 종료된 세션을 ACTIVE로 되돌리는 상태 역행은 CS-006으로 차단한다. */
        if (captureSession.getStatus() == CaptureSessionStatus.ENDED) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_ENDED);
        }

        /* 한 요청 안의 resumedAt과 updatedAt이 같도록 현재 KST 시각을 한 번만 읽어 전이한다. */
        CaptureSession resumedSession = captureSession.resume(LocalDateTime.now(clock));
        CaptureSession savedSession = captureSessionControlRepository.save(resumedSession);

        /* 프레젠테이션 계층에는 기존 세션 식별자와 재개 상태·시각만 전달한다. */
        return new CaptureSessionResumeResult(
                savedSession.getId(),
                savedSession.getStatus(),
                savedSession.isPaused(),
                savedSession.getUpdatedAt()
        );
    }

    /* 인증 회사·구성원과 Path 회의 식별자의 기본 형식을 저장소 호출 전에 검증한다. */
    private void validateCommand(Long companyId, Long requesterMemberId, Long meetingId) {
        /* null 또는 양수가 아닌 식별자는 공통 입력 오류로 일관되게 반환한다. */
        if (companyId == null
                || companyId <= 0L
                || requesterMemberId == null
                || requesterMemberId <= 0L
                || meetingId == null
                || meetingId <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
