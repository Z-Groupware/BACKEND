package com.module06.backend.meeting.application.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.ReplaceMeetingAttendeesCommand;
import com.module06.backend.meeting.application.event.MeetingAttendeesAddedEvent;
import com.module06.backend.meeting.application.port.out.MeetingEventPublisher;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;
import com.module06.backend.meeting.application.usecase.ReplaceMeetingAttendeesUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingLockRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * MEET-09 참석자 명단 전체 교체를 조율하는 애플리케이션 서비스다.
 *
 * 회의 열람 저장소로 회사·권한·상태를 확인하고, B 구성원 Port로 최종 명단을 검증한 뒤
 * 기존 명단과 목표 명단의 차이를 원자적으로 저장하고 새 참석자만 알림 이벤트로 발행한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingAttendeeCommandService implements ReplaceMeetingAttendeesUseCase {

    /* 회사 범위 회의를 잠그고 최신 기존 참석자 식별자를 읽는 명령 전용 저장소다. */
    private final MeetingLockRepository meetingLockRepository;

    /* 참석자 차이를 같은 트랜잭션에서 반영하는 회의 쓰기 저장소다. */
    private final MeetingRepository meetingRepository;

    /* 최종 명단의 회사 소속·활성 상태와 표시 정보를 일괄 조회하는 B 연동 Port다. */
    private final MemberQueryPort memberQueryPort;

    /* 새로 추가된 참석자 초대 이벤트를 Spring 이벤트 채널에 발행하는 Port다. */
    private final MeetingEventPublisher meetingEventPublisher;

    /* 회의 참석자 명단을 개설자 포함 최종 목록으로 전체 교체한다. */
    @Override
    @Transactional
    public MeetingAttendeeUpdateResult replaceMeetingAttendees(ReplaceMeetingAttendeesCommand command) {
        /* Controller 밖에서 호출돼도 잘못된 인증·식별자·명단 값이 저장소에 도달하지 않게 한다. */
        validateRequiredValues(command);

        /* 회사 조건을 조회에 포함해 타 회사 회의를 존재하지 않는 회의처럼 처리한다. */
        MeetingSnapshot meeting = meetingLockRepository
                .findMeetingForUpdate(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* host·OWNER·ADMIN만 회의 참석자 명단을 관리할 수 있다. */
        if (!canManageAttendees(command, meeting)) {
            throw new BusinessException(MeetingErrorCode.MEETING_HOST_ONLY);
        }

        /* 종료·취소된 회의의 확정 명단과 이력은 변경할 수 없다. */
        if (meeting.status() == MeetingStatus.DONE
                || meeting.status() == MeetingStatus.CANCELED) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_DONE);
        }

        /* 요청 명단 앞에 개설자를 자동 포함하고 중복 식별자를 입력 순서대로 제거한다. */
        List<Long> finalMemberIds = normalizeAttendees(
                meeting.hostMemberId(),
                command.attendeeMemberIds()
        );

        /* 최종 명단 전체가 같은 회사의 삭제되지 않은 구성원인지 한 번에 검증한다. */
        Map<Long, MemberSnapshot> members = findAndValidateMembers(
                meeting.companyId(),
                finalMemberIds
        );

        /* 기존 명단에 없던 구성원만 교체 완료 후 초대 알림 대상으로 계산한다. */
        Set<Long> existingMemberIds = Set.copyOf(meeting.attendeeMemberIds());
        List<Long> addedMemberIds = finalMemberIds.stream()
                .filter(memberId -> !existingMemberIds.contains(memberId))
                .toList();

        /* 기존과 목표 명단의 차이를 같은 트랜잭션에서 반영해 즉시 입장 권한을 갱신한다. */
        meetingRepository.replaceAttendees(meeting.meetingId(), finalMemberIds);

        /* 새 참석자가 있을 때만 AFTER_COMMIT 소비용 초대 이벤트를 발행한다. */
        if (!addedMemberIds.isEmpty()) {
            meetingEventPublisher.publish(new MeetingAttendeesAddedEvent(
                    meeting.meetingId(),
                    meeting.companyId(),
                    meeting.hostMemberId(),
                    addedMemberIds,
                    meeting.title(),
                    meeting.startAt(),
                    meeting.endAt()
            ));
        }

        /* 개설자 우선 최종 식별자 순서대로 구성원 표시 정보를 응답 결과에 조립한다. */
        List<MeetingAttendeeUpdateResult.Attendee> attendees = finalMemberIds.stream()
                .map(members::get)
                .map(member -> new MeetingAttendeeUpdateResult.Attendee(
                        member.memberId(),
                        member.name(),
                        member.teamName()
                ))
                .toList();

        /* 대상 회의와 저장된 최종 명단을 MEET-09 결과로 반환한다. */
        return new MeetingAttendeeUpdateResult(meeting.meetingId(), attendees);
    }

    /* 필수 인증 값과 Path 식별자 및 요청 명단이 올바른지 확인한다. */
    private void validateRequiredValues(ReplaceMeetingAttendeesCommand command) {
        /* 인증 주체·회의·명단을 식별할 수 없으면 공통 입력값 오류로 거절한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.meetingId() == null
                || command.meetingId() <= 0L
                || command.attendeeMemberIds() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 내부 호출에서도 null·0·음수 참석자 식별자가 정규화 단계의 NPE로 번지지 않게 거절한다. */
        boolean containsInvalidMemberId = command.attendeeMemberIds().stream()
                .anyMatch(memberId -> memberId == null || memberId <= 0L);
        if (containsInvalidMemberId) {
            /* 형식 자체가 잘못된 요청은 구성원 존재 여부를 조회하기 전에 공통 400 오류로 처리한다. */
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 요청자가 대상 회의의 참석자 명단을 교체할 권한이 있는지 판단한다. */
    private boolean canManageAttendees(
            ReplaceMeetingAttendeesCommand command,
            MeetingSnapshot meeting
    ) {
        /* 인증 관리자 플래그와 OWNER·ADMIN 역할은 회사 내 회의 관리 권한을 가진다. */
        boolean elevated = command.requesterAdmin()
                || "OWNER".equals(command.requesterRole())
                || "ADMIN".equals(command.requesterRole());

        /* 일반 역할 사용자는 자신이 개설한 회의의 명단만 변경할 수 있다. */
        boolean host = meeting.hostMemberId().equals(command.requesterMemberId());

        /* 관리자 또는 개설자 조건 중 하나라도 만족하면 명단 교체를 허용한다. */
        return elevated || host;
    }

    /* 개설자를 첫 번째로 자동 포함하고 요청 명단의 중복을 제거한다. */
    private List<Long> normalizeAttendees(Long hostMemberId, List<Long> requestedMemberIds) {
        /* 개설자를 먼저 추가한 뒤 요청 순서대로 참석자를 추가해 안정적인 응답 순서를 만든다. */
        LinkedHashSet<Long> memberIds = new LinkedHashSet<>();
        memberIds.add(hostMemberId);
        memberIds.addAll(requestedMemberIds);

        /* 외부에서 최종 명단을 변경하지 못하도록 불변 목록으로 반환한다. */
        return List.copyOf(memberIds);
    }

    /* 최종 참석자 표시 정보를 일괄 조회하고 모든 식별자가 유효한지 확인한다. */
    private Map<Long, MemberSnapshot> findAndValidateMembers(Long companyId, List<Long> memberIds) {
        /* 조회 결과 순서와 무관하게 식별자로 표시 정보를 찾을 수 있도록 맵으로 정리한다. */
        Map<Long, MemberSnapshot> members = new LinkedHashMap<>();
        for (MemberSnapshot member : memberQueryPort.findActiveMembers(companyId, memberIds)) {
            members.put(member.memberId(), member);
        }

        /* 타 회사·삭제 구성원·누락 결과가 하나라도 있으면 명단 전체를 MT-010으로 거절한다. */
        if (members.size() != memberIds.size() || !members.keySet().containsAll(memberIds)) {
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }

        /* 응답 조립 도중 조회 결과가 변경되지 않도록 불변 맵으로 반환한다. */
        return Map.copyOf(members);
    }
}
