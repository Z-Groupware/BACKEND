package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.CancelMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingCanceledEvent;
import com.module06.backend.meeting.application.port.out.MeetingCancellationEventPublisher;
import com.module06.backend.meeting.application.usecase.CancelMeetingUseCase;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingCancellationRepository;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * MEET-06 시작 전 회의 취소의 테넌트·권한·상태·슬롯 해제를 한 트랜잭션으로 조율한다.
 *
 * 최초 취소만 상태 저장과 이벤트 발행을 수행하고, 재취소는 최초 취소 이력을 보존하는 멱등 성공이다.
 */
@Service
@RequiredArgsConstructor
public class MeetingCancellationService implements CancelMeetingUseCase {

    /* 회사 범위 회의를 잠그고 취소 상태 저장과 슬롯 해제를 수행하는 도메인 저장소다. */
    private final MeetingCancellationRepository meetingCancellationRepository;

    /* 취소 커밋 뒤 알림 처리를 시작할 내부 이벤트 발행 Port다. */
    private final MeetingCancellationEventPublisher meetingCancellationEventPublisher;

    /* 취소 상태·영속성·이벤트가 하나의 서버 시각을 사용하게 하는 기준 시계다. */
    private final Clock clock;

    /* 시작 전 회의를 취소하고 현재 예약 슬롯을 해제한다. */
    @Override
    @Transactional
    public void cancelMeeting(CancelMeetingCommand command) {
        /* Controller 밖의 내부 호출도 잘못된 인증·Path 값으로 저장소에 접근하지 못하게 한다. */
        validateCommand(command);

        /* 회사 조건을 잠금 조회에 포함해 타 회사 회의 존재 여부를 MT-001로 숨긴다. */
        Meeting current = meetingCancellationRepository
                .findForCancellation(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* host 또는 같은 회사 OWNER·ADMIN만 회의 취소와 예약 해제를 수행할 수 있다. */
        if (!canCancel(command, current)) {
            throw new BusinessException(MeetingErrorCode.MEETING_HOST_ONLY);
        }

        /* 재취소는 최초 canceledAt과 최초 이벤트를 보존하고 저장 없이 200으로 끝낸다. */
        if (current.getStatus() == MeetingStatus.CANCELED) {
            return;
        }

        /* 실제 시작 또는 종료된 회의는 확정된 시간축과 이력을 취소 상태로 되돌릴 수 없다. */
        if (current.getStatus() != MeetingStatus.SCHEDULED) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_STARTED);
        }

        /* 저장 상태와 이벤트가 어긋나지 않도록 서버의 현재 순간을 한 번만 읽는다. */
        LocalDateTime canceledAt = LocalDateTime.now(clock);

        /* CANCELED 상태 저장과 모든 예약 슬롯 삭제를 같은 저장소 트랜잭션 경계에서 수행한다. */
        Meeting canceled = meetingCancellationRepository.saveCancellationAndReleaseSlots(
                current.cancel(canceledAt)
        );

        /* 최초 취소 트랜잭션 안에서 발행해 F가 AFTER_COMMIT으로만 알림을 처리하게 한다. */
        meetingCancellationEventPublisher.publish(new MeetingCanceledEvent(
                canceled.getId(),
                canceled.getCompanyId(),
                canceled.getHostMemberId(),
                canceled.getAttendeeMemberIds(),
                canceled.getTitle(),
                canceled.getStartAt(),
                canceled.getCanceledAt()
        ));
    }

    /* 필수 인증 값과 Path 식별자의 기본 형식을 저장소 호출 전에 검증한다. */
    private void validateCommand(CancelMeetingCommand command) {
        /* 회사·요청자·역할·회의 식별자가 없으면 대상과 권한을 안전하게 판정할 수 없다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()
                || command.meetingId() == null
                || command.meetingId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 인증 사용자가 대상 회의를 취소할 host·OWNER·ADMIN 권한인지 판단한다. */
    private boolean canCancel(CancelMeetingCommand command, Meeting meeting) {
        /* 관리자 겸직 플래그와 대소문자를 정규화한 OWNER·ADMIN 역할을 관리 권한으로 인정한다. */
        String normalizedRole = command.requesterRole().trim().toUpperCase(Locale.ROOT);
        boolean elevated = command.requesterAdmin()
                || "OWNER".equals(normalizedRole)
                || "ADMIN".equals(normalizedRole);

        /* 일반 구성원은 자신이 개설한 회의만 취소할 수 있다. */
        return elevated || meeting.isHost(command.requesterMemberId());
    }
}
