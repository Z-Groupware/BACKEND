package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.CompleteMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingCompletionRequestedEvent;
import com.module06.backend.meeting.application.port.out.MeetingCompletionEventPublisher;
import com.module06.backend.meeting.application.result.MeetingCompletionResult;
import com.module06.backend.meeting.application.usecase.CompleteMeetingUseCase;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionControlRepository;
import com.module06.backend.meeting.domain.repository.MeetingCompletionRepository;
import com.module06.backend.meeting.exception.CaptureSessionErrorCode;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * MEET-08 회의·캡처 세션 종료와 비동기 분석 요청 발행을 한 트랜잭션으로 조율한다.
 *
 * D는 자신이 소유한 상태만 확정하며 A의 조립·분석 유스케이스는 직접 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MeetingCompletionService implements CompleteMeetingUseCase {

    /* 분석 접수를 즉시 표현하되 A의 summary_job 구현 타입에는 의존하지 않는 공개 상태다. */
    private static final String PENDING_PROCESSING_STATUS = "PENDING";

    /* 회사 범위 회의를 잠그고 완료 상태를 저장하는 D 도메인 저장소다. */
    private final MeetingCompletionRepository meetingCompletionRepository;

    /* 회의당 하나인 캡처 세션을 잠그고 ENDED 상태로 저장하는 D 도메인 저장소다. */
    private final CaptureSessionControlRepository captureSessionControlRepository;

    /* 커밋 이후 A가 조립 여부 확인과 분석을 시작할 이벤트를 발행하는 Port다. */
    private final MeetingCompletionEventPublisher meetingCompletionEventPublisher;

    /* 회의·세션 종료 시각과 이벤트 요청 시각을 같은 순간으로 맞추는 서버 시계다. */
    private final Clock clock;

    /* 진행 중 회의와 캡처 세션을 종료하고 분석 요청 이벤트를 발행한다. */
    @Override
    @Transactional
    public MeetingCompletionResult completeMeeting(CompleteMeetingCommand command) {
        /* Controller 밖의 내부 호출도 잘못된 인증·Path 값으로 저장소에 접근하지 못하게 한다. */
        validateCommand(command);

        /* 회사 조건을 잠금 조회에 포함해 타 회사 회의 존재 여부를 MT-001로 숨긴다. */
        Meeting meeting = meetingCompletionRepository
                .findForCompletion(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* host 또는 회사 OWNER·ADMIN만 회의 종료와 분석 시작을 요청할 수 있다. */
        if (!canComplete(command, meeting)) {
            throw new BusinessException(MeetingErrorCode.MEETING_HOST_ONLY);
        }

        /* 중복 분석 트리거를 막기 위해 이미 DONE인 요청은 멱등 성공이 아닌 MT-009로 거절한다. */
        if (meeting.getStatus() == MeetingStatus.DONE) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_DONE);
        }

        /* 한 번도 입장하지 않은 SCHEDULED 회의는 종료로 건너뛰지 못하게 MT-013으로 거절한다. */
        if (meeting.getStatus() == MeetingStatus.SCHEDULED) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_STARTED);
        }

        /* 같은 세션의 CAP-02·03 상태 전이와 종료를 직렬화하기 위해 세션 행을 잠근다. */
        CaptureSession captureSession = captureSessionControlRepository
                .findByMeetingIdForUpdate(meeting.getId())
                .orElseThrow(() -> new BusinessException(
                        CaptureSessionErrorCode.CAPTURE_SESSION_NOT_FOUND
                ));

        /* 이미 종료된 캡처 세션에서 분석 이벤트만 다시 발행되는 모순 상태를 차단한다. */
        if (captureSession.getStatus() == CaptureSessionStatus.ENDED) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_ENDED);
        }

        /* 회의·세션·이벤트가 같은 종료 순간을 사용하도록 서버 시계를 한 번만 읽는다. */
        LocalDateTime completedAt = LocalDateTime.now(clock);

        /* 처리 순서 계약에 따라 캡처 세션을 먼저 ENDED로 저장한다. */
        CaptureSession completedCaptureSession = captureSessionControlRepository.save(
                captureSession.end(completedAt)
        );

        /* 캡처 종료가 반영된 뒤 회의 실제 종료 시각과 DONE 상태를 저장한다. */
        Meeting completedMeeting = meetingCompletionRepository.saveCompleted(
                meeting.complete(completedAt)
        );

        /* 트랜잭션 안에서 발행해 A가 AFTER_COMMIT으로만 조립·분석을 시작하게 한다. */
        meetingCompletionEventPublisher.publish(new MeetingCompletionRequestedEvent(
                completedMeeting.getCompanyId(),
                completedMeeting.getId(),
                completedCaptureSession.getId(),
                completedAt
        ));

        /* D 저장 결과와 비동기 분석 접수 상태를 즉시 200 응답용 결과로 반환한다. */
        return new MeetingCompletionResult(
                completedMeeting.getId(),
                completedMeeting.getStatus(),
                PENDING_PROCESSING_STATUS,
                completedCaptureSession.getStatus(),
                completedMeeting.getStartedAt(),
                completedMeeting.getEndedAt(),
                completedMeeting.actualDurationMinutes()
        );
    }

    /* 필수 인증 값과 Path 식별자의 기본 형식을 저장소 호출 전에 검증한다. */
    private void validateCommand(CompleteMeetingCommand command) {
        /* null 또는 양수가 아닌 식별자는 공통 입력값 오류로 일관되게 반환한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.meetingId() == null
                || command.meetingId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 인증 사용자가 대상 회의를 종료할 권한이 있는지 판단한다. */
    private boolean canComplete(CompleteMeetingCommand command, Meeting meeting) {
        /* 보안 principal의 관리자 플래그와 OWNER·ADMIN 역할은 회사 회의를 관리할 수 있다. */
        boolean elevated = command.requesterAdmin()
                || "OWNER".equals(command.requesterRole())
                || "ADMIN".equals(command.requesterRole());

        /* 일반 역할 사용자는 자신이 개설한 회의만 종료할 수 있다. */
        return elevated || meeting.isHost(command.requesterMemberId());
    }
}
