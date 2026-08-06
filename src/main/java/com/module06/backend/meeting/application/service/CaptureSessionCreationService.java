package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionRepository;
import com.module06.backend.meeting.exception.CaptureSessionErrorCode;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * CAP-01에서 meeting 잠금이 필요한 검증과 capture_session INSERT만 짧은 트랜잭션으로 묶는다.
 *
 * B 도메인 roster 조회는 이 서비스 호출 전에 끝나므로 잠금 시간이 타 도메인 응답 지연에 묶이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CaptureSessionCreationService {

    /* 회의를 잠그고 캡처 세션을 조회·저장하는 D 도메인 저장소다. */
    private final CaptureSessionRepository captureSessionRepository;

    /* KST 시작 일시와 epoch 밀리초를 같은 순간에서 생성하기 위한 서버 시계다. */
    private final Clock clock;

    /* 최신 회의를 잠근 뒤 검증된 roster 스냅샷과 일치할 때만 세션을 저장한다. */
    @Transactional
    public CaptureSession create(
            StartCaptureSessionCommand command,
            List<Long> expectedAttendeeMemberIds
    ) {
        /* 같은 회의의 동시 시작을 직렬화하고 타 회사 회의 존재 여부를 숨긴다. */
        Meeting meeting = captureSessionRepository
                .findMeetingForStart(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* 사전 조회 뒤 host가 바뀌거나 잘못된 내부 호출이 들어와도 잠금 안에서 다시 검증한다. */
        validateHostAndStatus(meeting, command.requesterMemberId());

        /* 이름 조회 중 명단이 교체됐다면 오래된 roster로 세션을 확정하지 않고 전체 트랜잭션을 되돌린다. */
        if (!meeting.getAttendeeMemberIds().equals(expectedAttendeeMemberIds)) {
            throw new CaptureSessionRosterChangedException();
        }

        /* 사용자 친화적 오류를 먼저 반환하되 최종 중복 방지는 DB UNIQUE 제약에 맡긴다. */
        if (captureSessionRepository.existsByMeetingId(meeting.getId())) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_EXISTS);
        }

        /* 로컬 일시와 epoch가 어긋나지 않도록 잠금 트랜잭션 안에서 서버 순간을 한 번만 읽는다. */
        Instant startedInstant = clock.instant();
        LocalDateTime startedAt = LocalDateTime.ofInstant(startedInstant, clock.getZone());

        /* D 소유 값만 가진 세션을 저장하고 데이터베이스 생성 식별자를 반영한다. */
        return captureSessionRepository.save(CaptureSession.start(
                meeting.getId(),
                command.requesterMemberId(),
                startedAt,
                startedInstant.toEpochMilli()
        ));
    }

    /* 잠금 안에서 회의 개설자와 상태 전이 조건을 최종 검증한다. */
    private void validateHostAndStatus(Meeting meeting, Long requesterMemberId) {
        /* 화면 역할과 무관하게 실제 회의 개설자만 세션 생명주기를 제어할 수 있다. */
        if (!meeting.isHost(requesterMemberId)) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_HOST_ONLY);
        }

        /* 입장 전 예약 회의에는 캡처 시간축을 만들 수 없다. */
        if (meeting.getStatus() == MeetingStatus.SCHEDULED) {
            throw new BusinessException(MeetingErrorCode.MEETING_NOT_STARTED);
        }

        /* 종료된 회의가 새 캡처 세션으로 다시 활성화되는 상태 역행을 차단한다. */
        if (meeting.getStatus() == MeetingStatus.DONE) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_DONE);
        }
    }
}
