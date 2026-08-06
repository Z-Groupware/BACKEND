package com.module06.backend.meetingroom.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meetingroom.application.command.DeactivateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomReservationRepository;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * ROOM-05 회의실 비활성화의 권한·활성 상태·미래 예약 검증과 소프트 삭제를 조율하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class MeetingRoomDeactivationService implements DeactivateMeetingRoomUseCase {

    /* 회의실 비활성화가 허용된 회사 역할 목록이다. */
    private static final Set<String> MANAGEMENT_ROLES = Set.of("OWNER", "ADMIN");

    /* 활성 회의실 잠금 조회와 비활성 상태 저장을 수행하는 명령 저장소다. */
    private final MeetingRoomCommandRepository meetingRoomCommandRepository;

    /* 비활성화를 막는 미래 SCHEDULED 예약을 조회하는 저장소다. */
    private final MeetingRoomReservationRepository meetingRoomReservationRepository;

    /* 예약의 미래 여부와 deletedAt을 하나의 KST 시각으로 판정하기 위한 시계다. */
    private final Clock clock;

    /* 미래 예약이 없는 인증 회사의 활성 회의실을 소프트 삭제한다. */
    @Override
    @Transactional
    public void deactivateMeetingRoom(DeactivateMeetingRoomCommand command) {
        /* 웹 계층을 우회한 호출도 식별자와 관리 역할 계약을 지키도록 먼저 검증한다. */
        validateCommand(command);

        /* 신규 예약 생성과 경쟁하지 않도록 회사 범위의 활성 회의실 행을 쓰기 잠금으로 조회한다. */
        MeetingRoom current = meetingRoomCommandRepository
                .findActiveByIdForUpdate(command.companyId(), command.meetingRoomId())
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));

        /* 예약 조회 기준과 실제 비활성화 시각이 달라지지 않도록 현재 시각을 한 번만 계산한다. */
        LocalDateTime deactivatedAt = LocalDateTime.now(clock);

        /* 현재 이후 시작하는 같은 회사·회의실의 SCHEDULED 예약만 조회한다. */
        List<ScheduledMeetingReservation> futureReservations = meetingRoomReservationRepository
                .findFutureScheduledReservations(command.companyId(), command.meetingRoomId(), deactivatedAt);

        /* 예정 예약이 하나라도 남아 있으면 기존 예약을 사용할 회의실을 비활성화하지 않는다. */
        if (!futureReservations.isEmpty()) {
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_HAS_RESERVATION);
        }

        /* 기존 표시·운영 속성을 유지하고 deletedAt만 기록한 도메인 상태를 저장한다. */
        meetingRoomCommandRepository.save(current.deactivate(deactivatedAt));
    }

    /* 인증 회사·경로 식별자와 비활성화 권한을 서비스 경계에서 검증한다. */
    private void validateCommand(DeactivateMeetingRoomCommand command) {
        /* 명령이나 양수 식별자, 역할 값이 없으면 정상적인 비활성화 대상을 결정할 수 없다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.meetingRoomId() == null
                || command.meetingRoomId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 웹 인가를 우회한 내부 호출도 OWNER·ADMIN 외에는 회의실을 비활성화하지 못하게 한다. */
        String normalizedRole = command.requesterRole().trim().toUpperCase(Locale.ROOT);
        if (!MANAGEMENT_ROLES.contains(normalizedRole)) {
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_MANAGEMENT_FORBIDDEN);
        }
    }
}
