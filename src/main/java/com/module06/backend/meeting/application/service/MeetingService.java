package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.CreateMeetingCommand;
import com.module06.backend.meeting.application.command.CreateOnlineMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.ActionKind;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.ActionTeamReference;
import com.module06.backend.meeting.application.port.out.MeetingEventPublisher;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.port.out.OnlineMeetingRecordingPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.result.MeetingCreationResult;
import com.module06.backend.meeting.application.result.OnlineMeetingCreationResult;
import com.module06.backend.meeting.application.usecase.CreateMeetingUseCase;
import com.module06.backend.meeting.application.usecase.CreateOnlineMeetingUseCase;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingAgenda;
import com.module06.backend.meeting.domain.model.MeetingSlotGrid;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.domain.repository.MeetingTopicRepository;
import com.module06.backend.meeting.exception.MeetingErrorCode;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;
import com.module06.backend.project.exception.ProjectErrorCode;

/*
 * 회의 애플리케이션 서비스다.
 *
 * MEET-01의 외부 리소스 검증, 시간 규칙, 예약 저장, 내부 이벤트 발행을 하나의 트랜잭션으로 조율한다.
 * 다른 도메인의 실제 구현은 아웃바운드 Port 뒤에 숨기고 식별자와 읽기 모델만 전달받는다.
 */
@Service
@RequiredArgsConstructor
public class MeetingService implements CreateMeetingUseCase, CreateOnlineMeetingUseCase {

    /* 회의와 슬롯, 참석자를 원자적으로 저장하는 도메인 저장소다. */
    private final MeetingRepository meetingRepository;

    /* 저장된 회의 아래에 MAIN·SUB 안건 계층을 저장하는 도메인 저장소다. */
    private final MeetingTopicRepository meetingTopicRepository;

    /* 활성 회의실과 이용 가능 시간을 조회하는 D도메인 내부 포트다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 프로젝트 존재와 회사 소속을 확인하는 C도메인 연동 포트다. */
    private final ProjectQueryPort projectQueryPort;

    /* 참석자 유효성과 화면 표시 정보를 배치 조회하는 B도메인 연동 포트다. */
    private final MemberQueryPort memberQueryPort;

    /* 선택한 관련 액션의 존재와 회사 소속을 확인하는 C도메인 연동 포트다. */
    private final ActionQueryPort actionQueryPort;

    /* 예약 완료 이벤트를 발행하는 기술 독립 포트다. */
    private final MeetingEventPublisher meetingEventPublisher;

    /* S3에 직접 업로드된 온라인 녹음을 CAP recording으로 확정하는 연동 포트다. */
    private final OnlineMeetingRecordingPort onlineMeetingRecordingPort;

    /* 과거 예약 판정을 테스트 가능하게 만드는 KST 기준 시계다. */
    private final Clock clock;

    /*
     * 회의실과 시간, 프로젝트, 참석자를 검증한 뒤 회의를 예약한다.
     *
     * @param command 인증 정보와 요청 본문을 합친 회의 개설 명령
     * @return 저장된 회의와 응답 표시 정보
     */
    @Override
    @Transactional
    public MeetingCreationResult createMeeting(CreateMeetingCommand command) {
        /* Controller 밖에서 호출돼도 필수 계약이 깨지지 않도록 기본 입력을 재검증한다. */
        validateRequiredValues(command);

        /* 역할별 상위 팀 액션 규칙과 host 외 참석자 최소 인원을 외부 조회 전에 검증한다. */
        validateRelatedActionPolicy(command.hostRole(), command.relatedActionId());
        validateInvitedAttendees(command.hostMemberId(), command.attendeeMemberIds());

        /* 대주제와 소주제 계약을 검증하고 저장에 사용할 불변 안건 묶음을 만든다. */
        MeetingAgenda agenda = MeetingAgenda.create(command.mainTopic(), command.subTopics());

        /* 시간 자체의 순서와 30분 그리드, 과거 예약 여부를 먼저 검증한다. */
        validateTime(command.startAt(), command.endAt());

        /* 테넌트 범위 안의 활성 회의실을 조회하고 이용 가능 시간까지 검증한다. */
        MeetingRoomSnapshot room = meetingRoomQueryPort
                .findActiveMeetingRoom(command.companyId(), command.meetingRoomId())
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));
        validateMeetingRoomHours(command.startAt(), command.endAt(), room);

        /* 프로젝트 태그는 필수이므로 요청 회사의 활성 프로젝트인지 확인한다. */
        if (!projectQueryPort.existsActiveProject(command.companyId(), command.projectId())) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        /* 개설자를 포함한 중복 없는 최종 참석자 식별자를 먼저 만든다. */
        List<Long> attendeeMemberIds = normalizeAttendeeMemberIds(
                command.hostMemberId(),
                command.attendeeMemberIds()
        );

        /* 참석자 전원이 같은 회사의 활성 구성원인지 확인하고 실제 개설자 소속 팀을 확보한다. */
        Map<Long, MemberSnapshot> members = validateAndIndexMembers(command.companyId(), attendeeMemberIds);
        MemberSnapshot host = members.get(command.hostMemberId());

        /* 인증 토큰의 팀 값 대신 B 도메인에서 조회한 실제 개설자 팀으로 액션 정합성을 검증한다. */
        validateRelatedActionTeam(command.companyId(), command.relatedActionId(), host.teamId());

        /* 개설자를 자동 포함한 중복 없는 참석자 식별자 순서를 만든다. */
        Meeting meeting = Meeting.create(
                command.companyId(),
                command.projectId(),
                host.teamId(),
                command.meetingRoomId(),
                command.hostMemberId(),
                command.title(),
                command.startAt(),
                command.endAt(),
                command.recordingConsent(),
                command.relatedActionId(),
                command.attendeeMemberIds()
        );

        /* 슬롯 PK 충돌이 발생하면 저장소가 MT-002로 변환하고 전체 트랜잭션이 롤백된다. */
        Meeting savedMeeting = meetingRepository.saveReservation(meeting);

        /* 회의와 MAIN·SUB 안건은 같은 트랜잭션으로 묶여 어느 한쪽만 커밋되지 않게 한다. */
        meetingTopicRepository.saveAgenda(savedMeeting.getId(), agenda);

        /* 커밋 후 알림 소비자가 처리할 예약 완료 이벤트를 발행한다. */
        meetingEventPublisher.publish(new MeetingReservedEvent(
                savedMeeting.getId(),
                savedMeeting.getCompanyId(),
                savedMeeting.getHostMemberId(),
                savedMeeting.getAttendeeMemberIds(),
                savedMeeting.getTitle(),
                savedMeeting.getStartAt(),
                savedMeeting.getEndAt(),
                savedMeeting.isRecordingConsent()
        ));

        /* 저장 결과와 이미 조회한 읽기 모델을 사용해 추가 조회 없이 응답을 만든다. */
        return toResult(savedMeeting, room, members);
    }

    /*
     * 회의실과 예약 시간 없이 프로젝트, 참석자만 검증한 뒤 비대면 회의를 개설한다(MEET-18).
     *
     * MEET-01과 달리 회의실·시간 검증을 생략하고 예약 완료 알림도 발행하지 않는다.
     *
     * @param command 인증 정보와 요청 본문을 합친 온라인 회의 개설 명령
     * @return 저장된 회의와 응답 표시 정보
     */
    @Override
    @Transactional
    public OnlineMeetingCreationResult createOnlineMeeting(CreateOnlineMeetingCommand command) {
        /* Controller 밖에서 호출돼도 필수 계약이 깨지지 않도록 기본 입력을 재검증한다. */
        validateRequiredOnlineValues(command);

        /* 회의 검증 도중 실패해도 선행 업로드된 S3 파일이 남지 않도록 롤백 보상을 먼저 등록한다. */
        CreateOnlineMeetingCommand.RecordingReference recording = command.recording();
        onlineMeetingRecordingPort.prepare(new OnlineMeetingRecordingPort.Preparation(
                command.companyId(),
                command.hostMemberId(),
                recording.s3Key(),
                recording.fileName(),
                recording.contentType(),
                recording.sizeBytes()
        ));

        /* 역할별 상위 팀 액션 규칙과 host 외 참석자 최소 인원을 외부 조회 전에 검증한다. */
        validateRelatedActionPolicy(command.hostRole(), command.relatedActionId());
        validateInvitedAttendees(command.hostMemberId(), command.attendeeMemberIds());

        /* 대주제와 소주제 계약을 검증하고 저장에 사용할 불변 안건 묶음을 만든다. */
        MeetingAgenda agenda = MeetingAgenda.create(command.mainTopic(), command.subTopics());

        /* 프로젝트 태그는 필수이므로 요청 회사의 활성 프로젝트인지 확인한다. */
        if (!projectQueryPort.existsActiveProject(command.companyId(), command.projectId())) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        /* 개설자를 포함한 중복 없는 최종 참석자 식별자를 먼저 만든다. */
        List<Long> attendeeMemberIds = normalizeAttendeeMemberIds(
                command.hostMemberId(),
                command.attendeeMemberIds()
        );

        /* 참석자 전원이 같은 회사의 활성 구성원인지 확인하고 실제 개설자 소속 팀을 확보한다. */
        Map<Long, MemberSnapshot> members = validateAndIndexMembers(command.companyId(), attendeeMemberIds);
        MemberSnapshot host = members.get(command.hostMemberId());

        /* 인증 토큰의 팀 값 대신 B 도메인에서 조회한 실제 개설자 팀으로 액션 정합성을 검증한다. */
        validateRelatedActionTeam(command.companyId(), command.relatedActionId(), host.teamId());

        /* 회의실과 예약 시간이 없는 온라인 회의를 생성한다. */
        Meeting meeting = Meeting.createOnline(
                command.companyId(),
                command.projectId(),
                host.teamId(),
                command.hostMemberId(),
                command.title(),
                command.recordingConsent(),
                command.relatedActionId(),
                command.attendeeMemberIds()
        );

        /*
         * 비대면 회의는 "개설 = 입장 = 종료"다 — 물리 회의의 MEET-08(MeetingCompletionService)은
         * 재사용하지 않는다. 그 로직은 CaptureSession이 반드시 있어야 하고(온라인 회의는 세션
         * 자체가 안 생김) MeetingCompletionRequestedEvent를 발행해 자동 조립·자동 분석 트리거를
         * 깨우는데, 개설 시점엔 녹음 파일이 아직 없어 그 자동 트리거가 그대로 발동하면 안 된다.
         * 그래서 도메인 전이만 체이닝해 캡처 세션·이벤트 없이 DONE 회의를 만든다. 시작·종료
         * 시각이 같아 실측 시간은 0분이지만 표시상의 문제일 뿐이다. 녹음은 프론트가 S3에 먼저
         * 직접 업로드하고, 아래 recording 확정이 커밋된 뒤 전체 파일 STT를 자동 시작한다.
         */
        LocalDateTime completedAt = LocalDateTime.now(clock);
        Meeting completedMeeting = meeting.enter(completedAt).complete(completedAt);

        /* 예약 슬롯이 없으므로 회의와 참석자만 저장한다. */
        Meeting savedMeeting = meetingRepository.saveOnlineReservation(completedMeeting);

        /* 회의와 MAIN·SUB 안건은 같은 트랜잭션으로 묶여 어느 한쪽만 커밋되지 않게 한다. */
        meetingTopicRepository.saveAgenda(savedMeeting.getId(), agenda);

        /* S3 객체 검증과 recording 저장도 같은 DB 트랜잭션에 참여시켜 일부 저장을 막는다. */
        onlineMeetingRecordingPort.register(new OnlineMeetingRecordingPort.Registration(
                savedMeeting.getCompanyId(),
                savedMeeting.getHostMemberId(),
                savedMeeting.getProjectId(),
                savedMeeting.getId(),
                recording.s3Key(),
                recording.fileName(),
                recording.contentType(),
                recording.sizeBytes()
        ));

        /* 비대면 회의 개설은 합의된 정책에 따라 예약 완료 알림을 발행하지 않는다. */
        return toOnlineResult(savedMeeting, members);
    }

    /* 필수 인증 값과 본문 값이 누락되면 공통 입력 오류로 거절한다. */
    private void validateRequiredValues(CreateMeetingCommand command) {
        /* 명령 객체 자체가 없거나 필수 식별자와 값이 빠진 경우 유스케이스를 진행할 수 없다. */
        if (command == null
                || command.companyId() == null
                || command.hostMemberId() == null
                || command.hostRole() == null
                || command.hostRole().isBlank()
                || command.projectId() == null
                || command.meetingRoomId() == null
                || command.title() == null
                || command.title().isBlank()
                || command.startAt() == null
                || command.endAt() == null
                || command.attendeeMemberIds() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 회의실·시간이 없는 MEET-18 필수 인증·본문 값이 누락되면 공통 입력 오류로 거절한다. */
    private void validateRequiredOnlineValues(CreateOnlineMeetingCommand command) {
        /* 명령 객체 자체가 없거나 필수 식별자와 값이 빠진 경우 유스케이스를 진행할 수 없다. */
        if (command == null
                || command.companyId() == null
                || command.hostMemberId() == null
                || command.hostRole() == null
                || command.hostRole().isBlank()
                || command.projectId() == null
                || command.title() == null
                || command.title().isBlank()
                || command.attendeeMemberIds() == null
                || command.recording() == null
                || command.recording().s3Key() == null
                || command.recording().fileName() == null
                || command.recording().contentType() == null
                || command.recording().sizeBytes() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* OWNER와 그 외 역할의 상위 팀 액션 입력 규칙을 검증한다. */
    private void validateRelatedActionPolicy(String hostRole, Long relatedActionId) {
        /* OWNER는 상위 팀 액션을 지정할 수 없고 다른 역할은 반드시 지정해야 한다. */
        boolean ownerWithAction = "OWNER".equals(hostRole) && relatedActionId != null;
        boolean nonOwnerWithoutAction = !"OWNER".equals(hostRole) && relatedActionId == null;
        if (ownerWithAction || nonOwnerWithoutAction) {
            throw new BusinessException(MeetingErrorCode.INVALID_RELATED_ACTION_POLICY);
        }
    }

    /* OWNER 외 개설자가 선택한 상위 액션이 같은 팀의 TEAM 액션인지 검증한다. MEET-01과 MEET-18이 함께 사용한다. */
    private void validateRelatedActionTeam(Long companyId, Long relatedActionId, Long actualHostTeamId) {
        /* OWNER는 역할 정책상 액션이 없으므로 C 도메인을 조회하지 않는다. */
        if (relatedActionId == null) {
            return;
        }

        /* 회사 범위에 존재하지 않는 액션은 기존 MEET-01 계약인 AC-001로 숨긴다. */
        ActionTeamReference action = actionQueryPort
                .findActionTeamReference(companyId, relatedActionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));

        /* 회의의 상위 액션은 팀 단위 액션이어야 하며 개인 액션은 연결할 수 없다. */
        if (action.actionKind() != ActionKind.TEAM) {
            throw new BusinessException(MeetingErrorCode.RELATED_ACTION_NOT_TEAM_ACTION);
        }

        /* 다른 팀의 액션을 연결하면 이후 개인 액션이 잘못된 팀 액션에 종속되므로 거절한다. */
        if (actualHostTeamId == null || !actualHostTeamId.equals(action.teamId())) {
            throw new BusinessException(MeetingErrorCode.RELATED_ACTION_TEAM_MISMATCH);
        }
    }

    /* 개설자를 첫 번째로 포함하고 요청 명단의 중복을 제거한 최종 참석자 식별자 목록을 만든다. */
    private List<Long> normalizeAttendeeMemberIds(Long hostMemberId, List<Long> attendeeMemberIds) {
        /* 도메인 생성 규칙과 동일한 삽입 순서 집합으로 B 도메인 배치 조회 대상을 확정한다. */
        LinkedHashSet<Long> normalizedMemberIds = new LinkedHashSet<>();
        normalizedMemberIds.add(hostMemberId);
        normalizedMemberIds.addAll(attendeeMemberIds);
        return List.copyOf(normalizedMemberIds);
    }

    /* 개설자를 제외하고 실제 초대된 참석자가 한 명 이상인지 검증한다. */
    private void validateInvitedAttendees(Long hostMemberId, List<Long> attendeeMemberIds) {
        /* 서비스 직접 호출에서도 null 식별자가 정규화 과정의 500 오류로 번지지 않도록 거절한다. */
        if (attendeeMemberIds.stream().anyMatch(memberId -> memberId == null)) {
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }

        /* host 중복 입력은 자동 제거되므로 host가 아닌 서로 다른 식별자를 기준으로 센다. */
        long invitedAttendeeCount = attendeeMemberIds.stream()
                .filter(memberId -> !hostMemberId.equals(memberId))
                .distinct()
                .count();
        if (invitedAttendeeCount < 1) {
            throw new BusinessException(MeetingErrorCode.MEETING_ATTENDEE_REQUIRED);
        }
    }

    /* 시간 범위와 30분 그리드, 과거 예약 여부를 명세 순서대로 검증한다. */
    private void validateTime(LocalDateTime startAt, LocalDateTime endAt) {
        /* 종료 시각은 시작 시각보다 반드시 늦어야 한다. */
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(MeetingErrorCode.INVALID_MEETING_TIME_RANGE);
        }

        /* 두 경계 모두 정각 또는 30분이고 초 이하 값이 없어야 슬롯 PK와 정확히 맞는다. */
        if (!isThirtyMinuteGrid(startAt) || !isThirtyMinuteGrid(endAt)) {
            throw new BusinessException(MeetingErrorCode.INVALID_MEETING_SLOT);
        }

        /* KST 현재 시각보다 이른 시작 시각은 예약할 수 없다. */
        if (startAt.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(MeetingErrorCode.PAST_MEETING_TIME);
        }
    }

    /* 한 시각이 초·나노초가 없는 30분 그리드인지 판단한다. */
    private boolean isThirtyMinuteGrid(LocalDateTime value) {
        /* 00분 또는 30분이고 초 이하 정밀도가 모두 0이어야 한다. */
        return value.getMinute() % MeetingSlotGrid.SLOT_MINUTES == 0
                && value.getSecond() == 0
                && value.getNano() == 0;
    }

    /* 예약 구간 전체가 같은 날의 회의실 이용 가능 범위에 포함되는지 검증한다. */
    private void validateMeetingRoomHours(
            LocalDateTime startAt,
            LocalDateTime endAt,
            MeetingRoomSnapshot room
    ) {
        /* 일일 이용 시간 계약이므로 날짜를 넘는 예약은 이용 가능 범위를 벗어난 것으로 처리한다. */
        boolean differentDay = !startAt.toLocalDate().equals(endAt.toLocalDate());

        /* 시작은 availableFrom 이상, 종료는 availableTo 이하여야 한다. */
        LocalTime startTime = startAt.toLocalTime();
        LocalTime endTime = endAt.toLocalTime();
        boolean startsTooEarly = startTime.isBefore(room.availableFrom());
        boolean endsTooLate = endTime.isAfter(room.availableTo());

        /* 어느 조건이든 벗어나면 동일한 MT-004 계약으로 응답한다. */
        if (differentDay || startsTooEarly || endsTooLate) {
            throw new BusinessException(MeetingErrorCode.OUTSIDE_MEETING_ROOM_HOURS);
        }
    }

    /* 활성 구성원을 배치 조회하고 요청한 모든 식별자가 존재하는지 검증한다. */
    private Map<Long, MemberSnapshot> validateAndIndexMembers(Long companyId, List<Long> memberIds) {
        /* 응답 순서를 보존하기 위해 조회 결과를 삽입 순서 맵으로 정리한다. */
        Map<Long, MemberSnapshot> membersById = new LinkedHashMap<>();
        for (MemberSnapshot member : memberQueryPort.findActiveMembers(companyId, memberIds)) {
            membersById.put(member.memberId(), member);
        }

        /* 다른 회사·삭제 구성원·누락된 식별자가 하나라도 있으면 명단 전체를 거절한다. */
        if (membersById.size() != memberIds.size() || !membersById.keySet().containsAll(memberIds)) {
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }

        /* 검증된 구성원 맵을 응답 조립에서 재사용한다. */
        return Map.copyOf(membersById);
    }

    /* 저장된 회의와 읽기 모델을 MEET-01 응답 결과로 변환한다. */
    private MeetingCreationResult toResult(
            Meeting meeting,
            MeetingRoomSnapshot room,
            Map<Long, MemberSnapshot> members
    ) {
        /* 개설자는 참석자 검증을 통과했으므로 반드시 맵에 존재한다. */
        MemberSnapshot host = members.get(meeting.getHostMemberId());

        /* 도메인이 보존한 순서대로 개설자 우선 참석자 응답을 만든다. */
        List<MeetingCreationResult.Attendee> attendees = meeting.getAttendeeMemberIds().stream()
                .map(members::get)
                .map(member -> new MeetingCreationResult.Attendee(
                        member.memberId(),
                        member.name(),
                        member.teamName()
                ))
                .toList();

        /* API에 필요한 값만 애플리케이션 결과 객체로 전달한다. */
        return new MeetingCreationResult(
                meeting.getId(),
                meeting.getStatus(),
                meeting.getTitle(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                meeting.isRecordingConsent(),
                new MeetingCreationResult.MeetingRoom(room.meetingRoomId(), room.name(), room.location()),
                new MeetingCreationResult.Host(host.memberId(), host.name()),
                attendees
        );
    }

    /* 저장된 온라인 회의와 읽기 모델을 MEET-18 응답 결과로 변환한다. */
    private OnlineMeetingCreationResult toOnlineResult(Meeting meeting, Map<Long, MemberSnapshot> members) {
        /* 개설자는 참석자 검증을 통과했으므로 반드시 맵에 존재한다. */
        MemberSnapshot host = members.get(meeting.getHostMemberId());

        /* 도메인이 보존한 순서대로 개설자 우선 참석자 응답을 만든다. */
        List<OnlineMeetingCreationResult.Attendee> attendees = meeting.getAttendeeMemberIds().stream()
                .map(members::get)
                .map(member -> new OnlineMeetingCreationResult.Attendee(
                        member.memberId(),
                        member.name(),
                        member.teamName()
                ))
                .toList();

        /* API에 필요한 값만 애플리케이션 결과 객체로 전달한다. */
        return new OnlineMeetingCreationResult(
                meeting.getId(),
                meeting.getStatus(),
                meeting.getTitle(),
                meeting.isRecordingConsent(),
                new OnlineMeetingCreationResult.Host(host.memberId(), host.name()),
                attendees
        );
    }
}
