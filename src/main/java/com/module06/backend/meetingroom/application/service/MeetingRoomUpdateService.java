package com.module06.backend.meetingroom.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meetingroom.application.command.UpdateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;
import com.module06.backend.meetingroom.domain.model.SlotGrid;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomReservationRepository;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * ROOM-04 회의실 부분 수정의 권한·입력·중복 이름·미래 예약 충돌을 조율하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class MeetingRoomUpdateService implements UpdateMeetingRoomUseCase {

    /* 회의실 수정이 허용된 회사 역할 목록이다. */
    private static final Set<String> MANAGEMENT_ROLES = Set.of("OWNER", "ADMIN");

    /* 활성 회의실 잠금 조회, 이름 중복 확인, 변경 저장을 수행하는 명령 저장소다. */
    private final MeetingRoomCommandRepository meetingRoomCommandRepository;

    /* 이용 가능 시간 축소와 충돌할 미래 SCHEDULED 예약을 조회하는 저장소다. */
    private final MeetingRoomReservationRepository meetingRoomReservationRepository;

    /* 미래 예약 판정이 운영 타임존과 테스트에서 동일하도록 주입받는 KST 기준 시계다. */
    private final Clock clock;

    /* 인증 회사의 활성 회의실을 검증된 최종 상태로 부분 수정한다. */
    @Override
    @Transactional
    public MeetingRoomUpdateResult updateMeetingRoom(UpdateMeetingRoomCommand command) {
        /* 웹 계층을 우회하는 호출도 역할과 기본 입력 계약을 동일하게 지키도록 먼저 검증한다. */
        validateCommand(command);

        /* 예약 생성과 동시에 이용 시간이 바뀌지 않도록 활성 회의실 행을 쓰기 잠금으로 조회한다. */
        MeetingRoom current = meetingRoomCommandRepository
                .findActiveByIdForUpdate(command.companyId(), command.meetingRoomId())
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));

        /* 전달되지 않은 필드는 기존 값을 유지하고 전달된 값만 최종 후보 상태에 반영한다. */
        String finalName = command.nameProvided() ? normalizeRequiredName(command.name()) : current.getName();
        String finalLocation = command.locationProvided()
                ? normalizeLocation(command.location())
                : current.getLocation();
        LocalTime finalAvailableFrom = command.availableFromProvided()
                ? requireTime(command.availableFrom())
                : current.getAvailableFrom();
        LocalTime finalAvailableTo = command.availableToProvided()
                ? requireTime(command.availableTo())
                : current.getAvailableTo();

        /* 부분 입력을 합친 최종 시간 범위가 도메인 슬롯 규칙 전체를 만족하는지 확인한다. */
        validateAvailableTime(finalAvailableFrom, finalAvailableTo);

        /* 이름이 실제로 바뀌는 경우 현재 회의실을 제외한 활성 이름 중복을 조기에 확인한다. */
        if (!finalName.equals(current.getName())
                && meetingRoomCommandRepository.existsActiveByCompanyIdAndNameExcludingId(
                        command.companyId(),
                        finalName,
                        command.meetingRoomId()
                )) {
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NAME_DUPLICATE);
        }

        /* 운영 시간을 좁히는 변경만 미래 예약을 조회해 불필요한 데이터베이스 접근을 피한다. */
        if (narrowsAvailableTime(current, finalAvailableFrom, finalAvailableTo)) {
            validateNoFutureReservationConflict(
                    command.companyId(),
                    command.meetingRoomId(),
                    finalAvailableFrom,
                    finalAvailableTo
            );
        }

        /* 검증된 최종 값으로 기존 식별자와 활성 상태가 유지되는 새 도메인 상태를 만든다. */
        MeetingRoom updated = current.update(
                finalName,
                finalLocation,
                finalAvailableFrom,
                finalAvailableTo
        );

        /* 데이터베이스 제약까지 통과한 저장 결과를 외부 응답용 읽기 모델로 변환한다. */
        return MeetingRoomUpdateResult.from(meetingRoomCommandRepository.save(updated));
    }

    /* 인증·경로·역할·빈 PATCH 요청을 서비스 경계에서 검증한다. */
    private void validateCommand(UpdateMeetingRoomCommand command) {
        /* 명령과 양수 식별자, 역할 값이 없으면 정상적인 수정 대상을 결정할 수 없다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.meetingRoomId() == null
                || command.meetingRoomId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 웹 인가를 우회한 내부 호출도 OWNER·ADMIN 외에는 회의실을 변경하지 못하게 한다. */
        String normalizedRole = command.requesterRole().trim().toUpperCase(Locale.ROOT);
        if (!MANAGEMENT_ROLES.contains(normalizedRole)) {
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_MANAGEMENT_FORBIDDEN);
        }

        /* PATCH 본문에 수정 필드가 하나도 없으면 의미 없는 저장을 실행하지 않는다. */
        if (!command.hasAnyChange()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 필수 이름의 null·공백·길이를 검증하고 가장자리 공백을 제거한다. */
    private String normalizeRequiredName(String name) {
        /* 명시적 null과 공백 이름은 활성 회의실의 필수 이름 계약을 깨므로 거절한다. */
        if (name == null || name.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 정규화한 실제 저장값이 DB VARCHAR 길이를 넘지 않게 한다. */
        String normalizedName = name.trim();
        if (normalizedName.length() > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedName;
    }

    /* 선택 위치를 null 또는 정규화된 문자열로 변환한다. */
    private String normalizeLocation(String location) {
        /* 명시적 null과 공백은 모두 위치 미등록 상태로 저장한다. */
        if (location == null || location.isBlank()) {
            return null;
        }

        /* 가장자리 공백을 제거한 위치가 DB VARCHAR 길이를 넘지 않게 한다. */
        String normalizedLocation = location.trim();
        if (normalizedLocation.length() > 150) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedLocation;
    }

    /* 명시적으로 전달된 시각이 null이 아닌지 확인한다. */
    private LocalTime requireTime(LocalTime time) {
        /* PATCH에서 시각 필드를 null로 보내 기존 필수값을 지울 수 없게 한다. */
        if (time == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return time;
    }

    /* 최종 이용 시간이 선후 관계와 30분 슬롯 경계를 만족하는지 검증한다. */
    private void validateAvailableTime(LocalTime availableFrom, LocalTime availableTo) {
        /* 종료 시각은 같은 날의 시작 시각보다 반드시 늦어야 한다. */
        if (!availableTo.isAfter(availableFrom)) {
            throw new BusinessException(MeetingRoomErrorCode.INVALID_AVAILABLE_TIME_RANGE);
        }

        /* 시작과 종료 모두 ROOM-02·MEET-01이 공유하는 정확한 30분 경계여야 한다. */
        if (!isSlotBoundary(availableFrom) || !isSlotBoundary(availableTo)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 한 시각이 초·나노초가 없는 30분 슬롯 경계인지 판단한다. */
    private boolean isSlotBoundary(LocalTime time) {
        /* 분은 0 또는 30이어야 하고 숨은 초 이하 정밀도도 없어야 한다. */
        return time.getMinute() % SlotGrid.SLOT_MINUTES == 0
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    /* 최종 이용 시간이 기존 범위의 시작을 늦추거나 종료를 앞당기는지 판단한다. */
    private boolean narrowsAvailableTime(MeetingRoom current, LocalTime availableFrom, LocalTime availableTo) {
        /* 어느 한쪽 경계라도 안쪽으로 이동하면 기존 예약과 충돌할 가능성이 생긴다. */
        return availableFrom.isAfter(current.getAvailableFrom())
                || availableTo.isBefore(current.getAvailableTo());
    }

    /* 축소한 이용 가능 시간 밖에 미래 SCHEDULED 예약이 없는지 확인한다. */
    private void validateNoFutureReservationConflict(
            Long companyId,
            Long meetingRoomId,
            LocalTime availableFrom,
            LocalTime availableTo
    ) {
        /* 현재 KST 시각 이후 시작하는 예정 예약만 한 번에 조회한다. */
        List<ScheduledMeetingReservation> reservations = meetingRoomReservationRepository
                .findFutureScheduledReservations(companyId, meetingRoomId, LocalDateTime.now(clock));

        /* 하나라도 새 범위를 벗어나면 기존 예약을 깨뜨리는 변경이므로 전체 수정을 거절한다. */
        boolean conflict = reservations.stream()
                .anyMatch(reservation -> isOutsideAvailableTime(reservation, availableFrom, availableTo));
        if (conflict) {
            throw new BusinessException(MeetingRoomErrorCode.RESERVATION_OUTSIDE_AVAILABLE_TIME);
        }
    }

    /* 예약 구간이 같은 날의 최종 이용 가능 시간 안에 완전히 포함되는지 판단한다. */
    private boolean isOutsideAvailableTime(
            ScheduledMeetingReservation reservation,
            LocalTime availableFrom,
            LocalTime availableTo
    ) {
        /* 잘못된 예약 읽기 모델도 허용 변경으로 오인하지 않도록 보수적으로 충돌 처리한다. */
        if (reservation == null || reservation.startAt() == null || reservation.endAt() == null) {
            return true;
        }

        /* 날짜를 넘는 예약 또는 어느 한 경계라도 최종 운영 범위를 벗어나면 충돌이다. */
        boolean differentDay = !reservation.startAt().toLocalDate().equals(reservation.endAt().toLocalDate());
        return differentDay
                || reservation.startAt().toLocalTime().isBefore(availableFrom)
                || reservation.endAt().toLocalTime().isAfter(availableTo);
    }
}
