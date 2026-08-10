package com.module06.backend.meetingroom.application.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meetingroom.application.command.CreateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.SlotGrid;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * ROOM-03 회의실 등록의 입력·도메인 규칙·이름 중복을 조율하는 애플리케이션 서비스다.
 */
@Service
@RequiredArgsConstructor
public class MeetingRoomCommandService implements CreateMeetingRoomUseCase {

    /* 회의실 이름 중복 확인과 신규 저장을 수행하는 명령 저장소다. */
    private final MeetingRoomCommandRepository meetingRoomCommandRepository;

    /* 인증 회사 범위에 검증된 신규 회의실을 등록한다. */
    @Override
    @Transactional
    public MeetingRoomCreationResult createMeetingRoom(CreateMeetingRoomCommand command) {
        /* Controller 밖의 내부 호출에서도 필수값과 문자열 길이 계약을 동일하게 보장한다. */
        validateRequiredValues(command);

        /* 이용 시간의 선후 관계와 ROOM-02가 사용하는 30분 그리드 정합성을 확인한다. */
        validateAvailableTime(command.availableFrom(), command.availableTo());

        /* 가장자리 공백이 다른 이름처럼 저장되지 않도록 중복 조회 전에 이름을 정규화한다. */
        String normalizedName = command.name().trim();
        if (meetingRoomCommandRepository.existsActiveByCompanyIdAndName(command.companyId(), normalizedName)) {
            /* 같은 회사의 활성 회의실만 이름 유일성 대상이며 비활성 회의실 이름은 재사용할 수 있다. */
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NAME_DUPLICATE);
        }

        /* 검증된 값으로 식별자와 비활성화 시각이 없는 신규 회의실 애그리거트를 만든다. */
        MeetingRoom meetingRoom = MeetingRoom.create(
                command.companyId(),
                normalizedName,
                command.location(),
                command.availableFrom(),
                command.availableTo()
        );

        /* 데이터베이스 생성 식별자가 반영된 회의실을 저장 결과로 받는다. */
        MeetingRoom savedMeetingRoom = meetingRoomCommandRepository.save(meetingRoom);

        /* 외부에 전체 도메인 객체를 노출하지 않고 생성된 회의실 식별자만 반환한다. */
        return new MeetingRoomCreationResult(savedMeetingRoom.getId());
    }

    /* 회사·이름·시간과 문자열 길이가 애플리케이션 계약에 맞는지 확인한다. */
    private void validateRequiredValues(CreateMeetingRoomCommand command) {
        /* 필수값 누락과 양수가 아닌 회사 식별자는 공통 입력 오류로 거절한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.name() == null
                || command.name().isBlank()
                || command.availableFrom() == null
                || command.availableTo() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* DB 컬럼보다 긴 이름이나 위치가 잘려 저장되지 않도록 서비스 경계에서도 제한한다. */
        String normalizedName = command.name().trim();
        String normalizedLocation = command.location() == null ? null : command.location().trim();
        if (normalizedName.length() > 100
                || normalizedLocation != null && normalizedLocation.length() > 150) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 이용 종료 시각이 늦고 두 시각 모두 30분 슬롯 경계인지 확인한다. */
    private void validateAvailableTime(LocalTime availableFrom, LocalTime availableTo) {
        /* 같은 날 안에서 종료 시각은 시작 시각보다 반드시 늦어야 한다. */
        if (!availableTo.isAfter(availableFrom)) {
            throw new BusinessException(MeetingRoomErrorCode.INVALID_AVAILABLE_TIME_RANGE);
        }

        /* ROOM-02와 MEET-01이 같은 그리드를 사용하도록 분·초·나노초 정밀도를 검사한다. */
        if (!isSlotBoundary(availableFrom) || !isSlotBoundary(availableTo)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 시각이 정확한 30분 그리드 경계인지 판단한다. */
    private boolean isSlotBoundary(LocalTime time) {
        /* 분은 0 또는 30이어야 하고 숨은 초·나노초 값도 없어야 한다. */
        return time.getMinute() % SlotGrid.SLOT_MINUTES == 0
                && time.getSecond() == 0
                && time.getNano() == 0;
    }
}
