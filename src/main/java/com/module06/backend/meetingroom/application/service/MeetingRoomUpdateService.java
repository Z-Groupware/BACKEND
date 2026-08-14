package com.module06.backend.meetingroom.application.service;

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
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * ROOM-04 회의실 부분 수정의 권한·입력·중복 이름을 조율하는 서비스다.
 * 모든 회의실은 24시간 예약되므로 개별 운영시간은 수정 대상이 아니다.
 */
@Service
@RequiredArgsConstructor
public class MeetingRoomUpdateService implements UpdateMeetingRoomUseCase {

    /* 회의실 수정이 허용된 회사 역할 목록이다. */
    private static final Set<String> MANAGEMENT_ROLES = Set.of("OWNER", "ADMIN");

    /* 활성 회의실 잠금 조회, 이름 중복 확인, 변경 저장을 수행하는 명령 저장소다. */
    private final MeetingRoomCommandRepository meetingRoomCommandRepository;

    /* 인증 회사의 활성 회의실을 검증된 최종 상태로 부분 수정한다. */
    @Override
    @Transactional
    public MeetingRoomUpdateResult updateMeetingRoom(UpdateMeetingRoomCommand command) {
        /* 웹 계층을 우회하는 호출도 역할과 기본 입력 계약을 동일하게 지키도록 먼저 검증한다. */
        validateCommand(command);

        /* 동일 회의실에 대한 동시 수정이 서로를 덮어쓰지 않도록 활성 행을 잠금 조회한다. */
        MeetingRoom current = meetingRoomCommandRepository
                .findActiveByIdForUpdate(command.companyId(), command.meetingRoomId())
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));

        /* 전달되지 않은 필드는 기존 값을 유지하고 전달된 값만 최종 후보에 반영한다. */
        String finalName = command.nameProvided() ? normalizeRequiredName(command.name()) : current.getName();
        String finalLocation = command.locationProvided()
                ? normalizeLocation(command.location())
                : current.getLocation();

        /* 이름이 실제로 바뀌 경우 현재 회의실을 제외한 활성 이름 중복을 확인한다. */
        if (!finalName.equals(current.getName())
                && meetingRoomCommandRepository.existsActiveByCompanyIdAndNameExcludingId(
                        command.companyId(),
                        finalName,
                        command.meetingRoomId()
                )) {
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NAME_DUPLICATE);
        }

        /* 식별자와 활성 상태를 유지하면서 관리 가능한 표시 정보만 변경한다. */
        MeetingRoom updated = current.update(finalName, finalLocation);

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
}
