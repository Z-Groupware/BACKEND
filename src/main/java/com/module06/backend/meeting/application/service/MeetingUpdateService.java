package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.command.UpdateMeetingCommand;
import com.module06.backend.meeting.application.event.MeetingUpdatedEvent;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.MeetingUpdateEventPublisher;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.result.MeetingUpdateResult;
import com.module06.backend.meeting.application.usecase.UpdateMeetingUseCase;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingSlotGrid;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingUpdateRepository;
import com.module06.backend.meeting.exception.MeetingErrorCode;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;
import com.module06.backend.project.exception.ProjectErrorCode;

/*
 * MEET-05 회의 부분 수정의 테넌트·권한·상태·예약 슬롯 변경을 조율하는 서비스다.
 *
 * 회의 행을 먼저 잠가 같은 회의의 상태 변경을 직렬화하고, 실제 예약 범위가 바뀌는 경우에만
 * 활성 회의실을 잠근 뒤 기존 슬롯과 신규 슬롯을 같은 트랜잭션에서 교체한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingUpdateService implements UpdateMeetingUseCase {

    /* 회사 범위 회의 잠금 조회와 meeting·slot 원자 저장을 수행하는 도메인 저장소다. */
    private final MeetingUpdateRepository meetingUpdateRepository;

    /* 변경할 활성 회의실 검증과 최종 회의실 표시 조회를 수행하는 D 내부 Port다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 변경할 프로젝트의 회사 소속과 활성 상태를 확인하는 C 연동 Port다. */
    private final ProjectQueryPort projectQueryPort;

    /* 수정 완료 알림을 커밋 후 소비할 수 있게 D 내부 이벤트로 발행하는 Port다. */
    private final MeetingUpdateEventPublisher meetingUpdateEventPublisher;

    /* 과거 시각 변경 판정을 운영 KST와 테스트에서 동일하게 만드는 기준 시계다. */
    private final Clock clock;

    /* 전달된 PATCH 필드만 최종 상태에 합치고 예약 충돌 없이 회의를 수정한다. */
    @Override
    @Transactional
    public MeetingUpdateResult updateMeeting(UpdateMeetingCommand command) {
        /* 웹 계층을 우회한 호출도 인증·경로·PATCH 기본 계약을 동일하게 지키도록 검증한다. */
        validateCommand(command);

        /* 회사 조건이 포함된 잠금 조회로 타 회사 회의 존재 여부를 MT-001 뒤에 숨긴다. */
        Meeting current = meetingUpdateRepository
                .findForUpdate(command.companyId(), command.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* host 또는 회사 OWNER·ADMIN만 회의 메타와 예약 범위를 변경할 수 있다. */
        if (!canManageMeeting(command, current)) {
            throw new BusinessException(MeetingErrorCode.MEETING_HOST_ONLY);
        }

        /* 실제 입장 또는 종료가 시작된 회의는 확정된 캡처 시간축 때문에 수정할 수 없다. */
        if (current.getStatus() != MeetingStatus.SCHEDULED) {
            throw new BusinessException(MeetingErrorCode.MEETING_ALREADY_STARTED);
        }

        /* 제공된 값은 검증·정규화하고 제공되지 않은 값은 잠금 조회한 최신 값을 유지한다. */
        String finalTitle = command.titleProvided()
                ? normalizeTitle(command.title())
                : current.getTitle();
        Long finalProjectId = command.projectIdProvided()
                ? requirePositiveId(command.projectId())
                : current.getProjectId();
        Long finalMeetingRoomId = command.meetingRoomIdProvided()
                ? requirePositiveId(command.meetingRoomId())
                : current.getMeetingRoomId();
        LocalDateTime finalStartAt = command.startAtProvided()
                ? requireDateTime(command.startAt())
                : current.getStartAt();
        LocalDateTime finalEndAt = command.endAtProvided()
                ? requireDateTime(command.endAt())
                : current.getEndAt();
        boolean finalRecordingConsent = command.recordingConsentProvided()
                ? requireBoolean(command.recordingConsent())
                : current.isRecordingConsent();

        /* 값이 실제로 달라진 프로젝트만 C 도메인의 활성 프로젝트 계약으로 검증한다. */
        boolean projectChanged = !Objects.equals(current.getProjectId(), finalProjectId);
        if (projectChanged && !projectQueryPort.existsActiveProject(command.companyId(), finalProjectId)) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        /* 회의실 또는 시간의 실제 값이 바뀌었는지 판단해 불필요한 슬롯 삭제·삽입을 막는다. */
        boolean reservationChanged = !Objects.equals(current.getMeetingRoomId(), finalMeetingRoomId)
                || !Objects.equals(current.getStartAt(), finalStartAt)
                || !Objects.equals(current.getEndAt(), finalEndAt);

        /* 예약 변경이면 MEET-01과 동일한 시간·회의실 규칙과 회의실 행 잠금을 적용한다. */
        MeetingRoomSnapshot room = reservationChanged
                ? validateChangedReservation(
                        command.companyId(),
                        finalMeetingRoomId,
                        finalStartAt,
                        finalEndAt
                )
                : findHistoricalMeetingRoom(command.companyId(), finalMeetingRoomId);

        /* 최종 값 중 하나라도 현재 회의와 다른 경우에만 UPDATE와 변경 이벤트를 발생시킨다. */
        boolean actualChange = reservationChanged
                || projectChanged
                || !Objects.equals(current.getTitle(), finalTitle)
                || current.isRecordingConsent() != finalRecordingConsent;

        /* 검증된 최종 값으로 참석자·개설자·상태를 유지한 새 도메인 상태를 만든다. */
        Meeting updated = current.updateSchedule(
                finalProjectId,
                finalMeetingRoomId,
                finalTitle,
                finalStartAt,
                finalEndAt,
                finalRecordingConsent
        );

        /* 동일 값 PATCH는 영속성 수정 시각과 알림을 만들지 않는 멱등 성공으로 처리한다. */
        Meeting saved = actualChange
                ? meetingUpdateRepository.saveUpdate(updated, reservationChanged)
                : current;

        /* 실제 변경이 커밋된 경우에만 참석자 알림용 최신 회의 스냅숏 이벤트를 발행한다. */
        if (actualChange) {
            meetingUpdateEventPublisher.publish(new MeetingUpdatedEvent(
                    saved.getId(),
                    saved.getCompanyId(),
                    saved.getHostMemberId(),
                    saved.getAttendeeMemberIds(),
                    saved.getProjectId(),
                    saved.getMeetingRoomId(),
                    saved.getTitle(),
                    saved.getStartAt(),
                    saved.getEndAt(),
                    saved.isRecordingConsent()
            ));
        }

        /* 저장된 회의와 검증 또는 이력 조회한 회의실 표시값으로 200 응답 결과를 만든다. */
        return new MeetingUpdateResult(
                saved.getId(),
                saved.getStatus(),
                saved.getStartAt(),
                saved.getEndAt(),
                new MeetingUpdateResult.MeetingRoom(room.meetingRoomId(), room.name())
        );
    }

    /* 인증 식별자와 PATCH 필드 존재 여부를 저장소 호출 전에 확인한다. */
    private void validateCommand(UpdateMeetingCommand command) {
        /* 회사·요청자·회의·역할이 없으면 권한과 수정 대상을 안전하게 결정할 수 없다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()
                || command.meetingId() == null
                || command.meetingId() <= 0L
                || !command.hasAnyChange()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 요청자가 대상 회의를 수정할 수 있는 host·OWNER·ADMIN 범위인지 판단한다. */
    private boolean canManageMeeting(UpdateMeetingCommand command, Meeting meeting) {
        /* 관리자 겸직 플래그와 OWNER·ADMIN 권한은 같은 회사의 전체 회의를 관리할 수 있다. */
        String normalizedRole = command.requesterRole().trim().toUpperCase(Locale.ROOT);
        boolean elevated = command.requesterAdmin()
                || "OWNER".equals(normalizedRole)
                || "ADMIN".equals(normalizedRole);

        /* 일반 구성원은 자신이 개설한 회의만 수정할 수 있다. */
        return elevated || meeting.isHost(command.requesterMemberId());
    }

    /* 제목의 null·공백·DB 최대 길이를 검증하고 가장자리 공백을 제거한다. */
    private String normalizeTitle(String title) {
        /* 명시적 null 또는 공백은 필수 제목 컬럼을 지우는 요청이므로 거절한다. */
        if (title == null || title.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 실제 저장 문자열을 기준으로 VARCHAR(200) 계약을 검사한다. */
        String normalized = title.trim();
        if (normalized.length() > 200) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    /* 명시적으로 전달된 프로젝트·회의실 식별자가 양수인지 검증한다. */
    private Long requirePositiveId(Long id) {
        /* null·0·음수 식별자는 외부 도메인 조회로 넘기지 않고 공통 입력 오류로 처리한다. */
        if (id == null || id <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return id;
    }

    /* 명시적으로 전달된 필수 예약 일시가 null이 아닌지 검증한다. */
    private LocalDateTime requireDateTime(LocalDateTime value) {
        /* PATCH의 명시적 null로 필수 예약 경계를 삭제하지 못하게 한다. */
        if (value == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return value;
    }

    /* 명시적으로 전달된 녹음 동의 값이 null이 아닌지 검증한다. */
    private boolean requireBoolean(Boolean value) {
        /* 미전달은 기존값 유지지만 명시적 null은 필수 boolean 컬럼을 지우는 잘못된 요청이다. */
        if (value == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return value;
    }

    /* 변경 예약의 시간 규칙과 활성 회의실 이용 범위를 검증하고 잠긴 회의실 스냅숏을 반환한다. */
    private MeetingRoomSnapshot validateChangedReservation(
            Long companyId,
            Long meetingRoomId,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        /* 종료는 시작보다 늦어야 빈 슬롯 또는 역방향 슬롯이 만들어지지 않는다. */
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(MeetingErrorCode.INVALID_MEETING_TIME_RANGE);
        }

        /* 두 경계가 정확한 30분 그리드인지 확인해 슬롯 복합 키 정밀도를 통일한다. */
        if (!isThirtyMinuteGrid(startAt) || !isThirtyMinuteGrid(endAt)) {
            throw new BusinessException(MeetingErrorCode.INVALID_MEETING_SLOT);
        }

        /* 변경된 시작 시각이 현재 KST보다 과거라면 과거 슬롯으로 예약을 이동할 수 없다. */
        if (startAt.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(MeetingErrorCode.PAST_MEETING_TIME);
        }

        /* 예약 생성·회의실 운영 시간 수정과 직렬화되도록 활성 회의실 행을 잠금 조회한다. */
        MeetingRoomSnapshot room = meetingRoomQueryPort
                .findActiveMeetingRoom(companyId, meetingRoomId)
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));

        /* 일일 운영 계약이므로 날짜를 넘거나 시작·종료가 운영 범위를 벗어나면 거절한다. */
        boolean differentDay = !startAt.toLocalDate().equals(endAt.toLocalDate());
        LocalTime startTime = startAt.toLocalTime();
        LocalTime endTime = endAt.toLocalTime();
        if (differentDay
                || startTime.isBefore(room.availableFrom())
                || endTime.isAfter(room.availableTo())) {
            throw new BusinessException(MeetingErrorCode.OUTSIDE_MEETING_ROOM_HOURS);
        }

        /* 검증과 잠금에 사용한 동일 스냅숏을 응답 조립에서도 재사용한다. */
        return room;
    }

    /* 한 일시가 초·나노초 없는 30분 슬롯 경계인지 판단한다. */
    private boolean isThirtyMinuteGrid(LocalDateTime value) {
        /* 분은 0 또는 30이어야 하며 숨은 초 이하 정밀도도 없어야 한다. */
        return value.getMinute() % MeetingSlotGrid.SLOT_MINUTES == 0
                && value.getSecond() == 0
                && value.getNano() == 0;
    }

    /* 예약이 바뀌지 않은 PATCH 응답을 위해 비활성 이력까지 포함한 회의실 표시값을 조회한다. */
    private MeetingRoomSnapshot findHistoricalMeetingRoom(Long companyId, Long meetingRoomId) {
        /* 수정되지 않은 기존 회의실은 이후 비활성화됐더라도 과거 예약 표시값으로 사용할 수 있다. */
        return meetingRoomQueryPort.findMeetingRooms(companyId, List.of(meetingRoomId))
                .stream()
                .filter(room -> Objects.equals(room.meetingRoomId(), meetingRoomId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));
    }
}
