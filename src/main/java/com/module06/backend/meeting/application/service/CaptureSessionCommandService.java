package com.module06.backend.meeting.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterEntry;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterType;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.CaptureSessionRepository;
import com.module06.backend.meeting.exception.CaptureSessionErrorCode;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * CAP-01의 회사 범위·회의 상태·host·단일 세션 검증과 roster 생성을 조율하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class CaptureSessionCommandService implements StartCaptureSessionUseCase {

    /* 회의를 잠그고 캡처 세션을 조회·저장하는 D 도메인 저장소다. */
    private final CaptureSessionRepository captureSessionRepository;

    /* 예약 참석자 식별자를 표시 이름으로 일괄 해석하는 B 도메인 조회 Port다. */
    private final MemberQueryPort memberQueryPort;

    /* meeting 시작 상태와 캡처 세션 저장을 짧은 트랜잭션으로 실행하는 내부 서비스다. */
    private final CaptureSessionCreationService captureSessionCreationService;

    /* 예약 회의의 host 요청으로 회의를 시작하고 회의당 하나의 ACTIVE 캡처 세션을 생성한다. */
    @Override
    public CaptureSessionStartResult startCaptureSession(StartCaptureSessionCommand command) {
        /* Controller를 우회한 호출도 잘못된 인증·Path 값으로 저장소를 조회하지 못하게 한다. */
        validateCommand(command);

        /* 명단 교체 경합이 한 번 발생하면 새 스냅샷과 이름으로 전체 흐름을 한 번 재시도한다. */
        for (int attempt = 0; attempt < 2; attempt++) {
            /* B 조회에 필요한 회의와 참석자 ID를 meeting 행 잠금 없이 먼저 읽는다. */
            Meeting meeting = captureSessionRepository
                    .findMeeting(command.companyId(), command.meetingId())
                    .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

            /* 불필요한 B 호출 전에 사전 스냅샷으로 host와 종료 상태를 빠르게 검증한다. */
            validateHostAndStatus(meeting, command.requesterMemberId());

            /* 잠금 밖에서 B 원본 이름과 명단 외 sentinel을 합쳐 닫힌 roster를 만든다. */
            List<RosterEntry> roster = createRoster(
                    command.companyId(),
                    meeting.getAttendeeMemberIds()
            );

            try {
                /* 짧은 별도 트랜잭션에서 명단 일치 확인과 회의 시작·세션 INSERT를 함께 수행한다. */
                CaptureSession savedSession = captureSessionCreationService.create(
                        command,
                        meeting.getAttendeeMemberIds()
                );

                /* 프레젠테이션에는 현재 녹음자가 아닌 세션 원본과 검증된 roster만 전달한다. */
                return new CaptureSessionStartResult(
                        savedSession.getId(),
                        savedSession.getStatus(),
                        savedSession.isPaused(),
                        savedSession.getStartedBy(),
                        savedSession.getStartedAtEpochMs(),
                        roster
                );
            } catch (CaptureSessionRosterChangedException exception) {
                /* 첫 경합은 최신 참석자로 다시 해석하고 두 번 연속 변경되면 안전하게 요청을 거절한다. */
                if (attempt == 1) {
                    throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
                }
            }
        }

        /* 반복문은 성공 반환 또는 두 번째 경합 예외로 끝나므로 이 경로는 방어적 오류 처리다. */
        throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
    }

    /* 잠금 없는 사전 스냅샷에서 host와 회의 상태를 검증해 불필요한 B 호출을 줄인다. */
    private void validateHostAndStatus(Meeting meeting, Long requesterMemberId) {
        /* 화면 역할과 무관하게 실제 회의 개설자만 세션 생명주기를 제어할 수 있다. */
        if (!meeting.isHost(requesterMemberId)) {
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_HOST_ONLY);
        }

        /* 종료·취소된 회의가 새 캡처 세션으로 다시 활성화되는 상태 역행을 차단한다. */
        if (meeting.getStatus() == MeetingStatus.DONE
                || meeting.getStatus() == MeetingStatus.CANCELED) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_DONE);
        }
    }

    /* 인증 식별자와 Path 회의 식별자의 기본 형식을 저장소 호출 전에 검증한다. */
    private void validateCommand(StartCaptureSessionCommand command) {
        /* null 또는 양수가 아닌 식별자는 공통 입력 오류로 일관되게 반환한다. */
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

    /* 예약 참석자 순서를 유지한 MEMBER 항목 뒤에 unknown_person sentinel을 하나 추가한다. */
    private List<RosterEntry> createRoster(Long companyId, List<Long> attendeeMemberIds) {
        /* 구성원 조회 결과를 ID로 색인해 원래 예약 명단의 순서를 그대로 사용할 수 있게 한다. */
        Map<Long, MemberSnapshot> membersById = new LinkedHashMap<>();
        for (MemberSnapshot member : memberQueryPort.findActiveMembers(companyId, attendeeMemberIds)) {
            /* 중복 응답은 최초 값을 유지해 한 구성원이 roster에 두 번 나타나지 않게 한다. */
            membersById.putIfAbsent(member.memberId(), member);
        }

        /* 예약 참석자와 명단 외 항목 하나를 담을 고정 크기 roster를 준비한다. */
        List<RosterEntry> roster = new ArrayList<>(attendeeMemberIds.size() + 1);
        for (Long memberId : attendeeMemberIds) {
            /* 삭제·타 회사 구성원으로 이름을 해석할 수 없으면 잘못된 참석자 원본으로 처리한다. */
            MemberSnapshot member = membersById.get(memberId);
            if (member == null) {
                throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
            }

            /* A의 화자 키 계약에 맞춰 실제 구성원 항목을 member:{id} 형식으로 만든다. */
            roster.add(new RosterEntry(
                    "member:" + memberId,
                    memberId,
                    member.name(),
                    RosterType.MEMBER
            ));
        }

        /* 외부 손님을 실제 member 행으로 만들지 않고 응답 전용 sentinel로 마지막에 추가한다. */
        roster.add(new RosterEntry(
                "unknown_person",
                null,
                "명단 외",
                RosterType.UNKNOWN
        ));

        /* 호출자가 변경할 수 없는 닫힌 roster를 반환한다. */
        return List.copyOf(roster);
    }
}
