package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.EnterMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingEntryResult;
import com.module06.backend.meeting.application.usecase.EnterMeetingUseCase;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingEntryPolicy;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingEntryRepository;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * MEET-07 회의 입장의 테넌트·참석자·시간·상태 검증과 최초 상태 전이를 조율하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class MeetingEntryService implements EnterMeetingUseCase {

    /* 회의 행 잠금 조회와 최초 입장 상태 저장을 수행하는 도메인 저장소다. */
    private final MeetingEntryRepository meetingEntryRepository;

    /* 입장 허용 시간과 최초 startedAt을 동일한 KST 현재 시각으로 판정하기 위한 시계다. */
    private final Clock clock;

    /* 예약 참석자를 회의에 입장시키고 최초 요청이면 회의를 IN_PROGRESS로 전이한다. */
    @Override
    @Transactional
    public MeetingEntryResult enterMeeting(EnterMeetingCommand command) {
        /* Controller를 우회한 호출도 잘못된 인증·Path 값이 저장소에 도달하지 않도록 검증한다. */
        validateCommand(command);

        /* 동시 첫 입장을 직렬화하고 타 회사 회의 존재 여부를 숨기도록 회사 조건과 함께 잠근다. */
        Meeting meeting = meetingEntryRepository
                .findForEntry(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* 회사 역할과 무관하게 실제 예약 명단에 없는 구성원은 회의 시간·상태를 보기 전에 거절한다. */
        if (!meeting.hasAttendee(command.requesterMemberId())) {
            throw new BusinessException(MeetingErrorCode.ATTENDEE_ONLY);
        }

        /* 한 요청 안의 입장 창 판정과 최초 시작 시각이 달라지지 않도록 현재 시각을 한 번만 읽는다. */
        LocalDateTime now = LocalDateTime.now(clock);

        /* DONE 상태는 예약 시간이 남아 있더라도 다시 진행 상태로 전이할 수 없다. */
        if (meeting.getStatus() == MeetingStatus.DONE) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_DONE);
        }

        /* 공유 정책의 입장 창 밖이라면 이른 요청과 종료 시각이 지난 요청을 서로 다른 코드로 구분한다. */
        if (!MeetingEntryPolicy.isEntryAvailable(meeting.getStartAt(), meeting.getEndAt(), now)) {
            if (now.isBefore(meeting.getStartAt().minusMinutes(MeetingEntryPolicy.EARLY_ENTRY_MINUTES))) {
                throw new BusinessException(MeetingErrorCode.ENTRY_NOT_AVAILABLE);
            }
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_DONE);
        }

        /* SCHEDULED만 실제 상태 저장이 필요하고 IN_PROGRESS 재입장은 기존 startedAt을 그대로 사용한다. */
        Meeting enteredMeeting = meeting.enter(now);
        if (meeting.getStatus() == MeetingStatus.SCHEDULED) {
            enteredMeeting = meetingEntryRepository.saveState(enteredMeeting);
        }

        /* 새 CAP 계약은 host만 세션을 제어하므로 입장 응답의 두 화면 플래그를 같은 원본에서 계산한다. */
        boolean isHost = enteredMeeting.isHost(command.requesterMemberId());
        return new MeetingEntryResult(
                enteredMeeting.getId(),
                enteredMeeting.getStatus(),
                enteredMeeting.getStartedAt(),
                enteredMeeting.getAttendeeMemberIds().size(),
                enteredMeeting.isRecordingConsent(),
                isHost,
                isHost
        );
    }

    /* 인증 회사·구성원과 Path 회의 식별자의 기본 형식을 검증한다. */
    private void validateCommand(EnterMeetingCommand command) {
        /* 식별할 수 없는 인증 주체나 회의는 저장소 쿼리 대신 공통 입력 오류로 처리한다. */
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
}
