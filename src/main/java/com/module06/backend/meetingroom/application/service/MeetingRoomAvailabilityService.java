package com.module06.backend.meetingroom.application.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailabilitySummary;
import com.module06.backend.meetingroom.application.result.MeetingRoomSlotSummary;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomAvailabilityUseCase;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.ReservedSlot;
import com.module06.backend.meetingroom.domain.model.SlotGrid;
import com.module06.backend.meetingroom.domain.repository.MeetingAttendanceRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomSlotRepository;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * ROOM-02 회의실 예약 현황 조회를 구현하는 애플리케이션 서비스다.
 *
 * 조회 대상 회의실, 그 회의실들이 하루 동안 점유당한 슬롯, 요청자의 참석 여부를 각각 한 번씩만 조회하고
 * 슬롯 그리드를 조립한다. 회의실 수와 무관하게 조회 횟수가 늘지 않아 N+1이 발생하지 않는다.
 * 조회 전용이므로 변경 감지 비용을 줄이기 위해 읽기 전용 트랜잭션을 사용한다.
 *
 * 연결된 클래스
 * - MeetingRoomRepository: 조회 대상 활성 회의실을 가져온다
 * - MeetingRoomSlotRepository: 물질화된 예약 슬롯을 가져온다
 * - MeetingAttendanceRepository: 회의 제목 열람 권한 판단에 필요한 참석 정보를 가져온다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingRoomAvailabilityService implements GetMeetingRoomAvailabilityUseCase {

    /* 기술 구현과 분리된 회의실 도메인 저장소 계약이다. */
    private final MeetingRoomRepository meetingRoomRepository;

    /* 예약된 30분 슬롯을 읽는 도메인 저장소 계약이다. */
    private final MeetingRoomSlotRepository meetingRoomSlotRepository;

    /* 요청자의 회의 참석 여부를 읽는 도메인 저장소 계약이다. */
    private final MeetingAttendanceRepository meetingAttendanceRepository;

    /*
     * 조회 날짜의 회의실별 슬롯 현황을 조립해 반환한다.
     *
     * @param query 회사·구성원 식별자와 조회 날짜, 회의실 필터를 담은 조회 조건
     * @return 회의실별 슬롯 현황 조회 결과
     * @throws BusinessException 회의실 식별자를 지정했으나 요청 회사의 활성 회의실이 아닌 경우
     */
    @Override
    public MeetingRoomAvailability getMeetingRoomAvailability(MeetingRoomAvailabilityQuery query) {
        /* 회의실 필터 유무에 따라 단건 또는 회사 전체 활성 회의실을 조회 대상으로 삼는다. */
        List<MeetingRoom> meetingRooms = findTargetMeetingRooms(query);

        /* 조회 대상 회의실이 하루 동안 점유당한 슬롯을 한 번에 읽는다. */
        List<ReservedSlot> reservedSlots = findReservedSlots(query, meetingRooms);

        /* 회의 제목을 노출할 수 있는 회의를 판단하기 위해 요청자가 참석자인 회의 식별자를 조회한다. */
        Set<Long> attendedMeetingIds = findAttendedMeetingIds(query.memberId(), reservedSlots);

        /* 슬롯을 회의실과 시작 시각으로 색인해 그리드 조립에서 목록을 반복 탐색하지 않게 한다. */
        Map<Long, Map<LocalTime, ReservedSlot>> reservedSlotIndex = indexByMeetingRoom(reservedSlots);

        /* 회의실마다 이용 가능 시간 안의 슬롯만 만들어 예약 상태를 채운다. */
        List<MeetingRoomAvailabilitySummary> summaries = meetingRooms.stream()
                .map(meetingRoom -> toAvailabilitySummary(
                        meetingRoom,
                        reservedSlotIndex.getOrDefault(meetingRoom.getId(), Map.of()),
                        attendedMeetingIds
                ))
                .toList();

        /* 슬롯 길이는 예약 그리드의 단일 기준이므로 도메인 상수를 그대로 응답에 담는다. */
        return new MeetingRoomAvailability(query.date(), SlotGrid.SLOT_MINUTES, summaries);
    }

    /*
     * 조회 조건에 해당하는 활성 회의실 목록을 조회한다.
     *
     * @param query 회의실 필터를 포함한 조회 조건
     * @return 조회 대상 활성 회의실 목록
     * @throws BusinessException 지정한 회의실이 요청 회사의 활성 회의실이 아닌 경우
     */
    private List<MeetingRoom> findTargetMeetingRooms(MeetingRoomAvailabilityQuery query) {
        /* 회의실을 지정하지 않은 요청은 회사의 활성 회의실 전체를 정렬된 순서로 조회한다. */
        if (!query.hasMeetingRoomFilter()) {
            return meetingRoomRepository.findAllActiveByCompanyId(query.companyId());
        }

        /* 다른 회사의 회의실이나 비활성 회의실은 존재 여부를 흘리지 않도록 404로 처리한다. */
        return List.of(
                meetingRoomRepository.findActiveById(query.companyId(), query.meetingRoomId())
                        .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND))
        );
    }

    /*
     * 조회 날짜 하루에 걸린 예약 슬롯을 조회한다.
     *
     * @param query 회사 식별자와 조회 날짜를 담은 조회 조건
     * @param meetingRooms 조회 대상 회의실 목록
     * @return 예약 슬롯 목록, 조회 대상이 없으면 빈 목록
     */
    private List<ReservedSlot> findReservedSlots(MeetingRoomAvailabilityQuery query, List<MeetingRoom> meetingRooms) {
        /* 회의실이 없으면 조회할 슬롯도 없으므로 불필요한 질의를 보내지 않는다. */
        if (meetingRooms.isEmpty()) {
            return List.of();
        }

        /* 조회 범위를 당일 00:00 이상 다음 날 00:00 미만으로 잡아 경계 시각이 두 날짜에 중복되지 않게 한다. */
        LocalDateTime dayStart = query.date().atStartOfDay();
        LocalDateTime nextDayStart = dayStart.plusDays(1);

        /* 회의실 식별자 목록으로 한 번에 조회해 회의실 수만큼 질의가 늘어나지 않게 한다. */
        List<Long> meetingRoomIds = meetingRooms.stream()
                .map(MeetingRoom::getId)
                .toList();

        return meetingRoomSlotRepository.findReservedSlots(query.companyId(), meetingRoomIds, dayStart, nextDayStart);
    }

    /*
     * 예약 슬롯이 가리키는 회의 중 요청자가 참석자인 회의 식별자를 조회한다.
     *
     * @param memberId 인증된 요청자의 구성원 식별자
     * @param reservedSlots 조회된 예약 슬롯 목록
     * @return 요청자가 참석자인 회의 식별자 집합, 판단할 수 없으면 빈 집합
     */
    private Set<Long> findAttendedMeetingIds(Long memberId, List<ReservedSlot> reservedSlots) {
        /* 요청자를 특정할 수 없거나 예약이 없으면 열람 권한이 있는 회의도 없다고 보고 제목을 모두 마스킹한다. */
        if (memberId == null || reservedSlots.isEmpty()) {
            return Set.of();
        }

        /* 같은 회의가 여러 슬롯을 점유하므로 중복을 제거한 식별자 목록으로 한 번만 조회한다. */
        List<Long> meetingIds = reservedSlots.stream()
                .map(ReservedSlot::meetingId)
                .distinct()
                .toList();

        return meetingAttendanceRepository.findAttendedMeetingIds(memberId, meetingIds);
    }

    /*
     * 예약 슬롯을 회의실 식별자와 슬롯 시작 시각으로 색인한다.
     *
     * @param reservedSlots 조회된 예약 슬롯 목록
     * @return 회의실별로 시작 시각을 키로 갖는 예약 슬롯 색인
     */
    private Map<Long, Map<LocalTime, ReservedSlot>> indexByMeetingRoom(List<ReservedSlot> reservedSlots) {
        /* (회의실, 슬롯 시각)이 슬롯 테이블의 복합 PK라 키 충돌은 발생하지 않지만, 병합 함수로 방어해 조회가 예외로 끊기지 않게 한다. */
        return reservedSlots.stream()
                .collect(Collectors.groupingBy(
                        ReservedSlot::meetingRoomId,
                        Collectors.toMap(ReservedSlot::startTime, Function.identity(), (first, ignored) -> first)
                ));
    }

    /*
     * 회의실 하나의 슬롯 현황을 조립한다.
     *
     * @param meetingRoom 조립 대상 회의실
     * @param reservedSlotsByStartTime 해당 회의실의 시작 시각별 예약 슬롯
     * @param attendedMeetingIds 요청자가 참석자인 회의 식별자 집합
     * @return 회의실 정보와 슬롯 현황을 담은 결과
     */
    private MeetingRoomAvailabilitySummary toAvailabilitySummary(
            MeetingRoom meetingRoom,
            Map<LocalTime, ReservedSlot> reservedSlotsByStartTime,
            Set<Long> attendedMeetingIds
    ) {
        /* 슬롯 칸은 회의실의 이용 가능 시간에서만 생성하므로 이용 시간 밖의 예약은 응답에 포함되지 않는다. */
        List<MeetingRoomSlotSummary> slots = meetingRoom.slotStartTimes().stream()
                .map(startTime -> toSlotSummary(
                        startTime,
                        reservedSlotsByStartTime.get(startTime),
                        attendedMeetingIds
                ))
                .toList();

        return new MeetingRoomAvailabilitySummary(
                meetingRoom.getId(),
                meetingRoom.getName(),
                meetingRoom.getAvailableFrom(),
                meetingRoom.getAvailableTo(),
                slots
        );
    }

    /*
     * 슬롯 한 칸의 예약 상태와 노출 가능한 회의 제목을 결정한다.
     *
     * @param startTime 슬롯 시작 시각
     * @param reservedSlot 해당 시작 시각의 예약 슬롯, 예약이 없으면 null
     * @param attendedMeetingIds 요청자가 참석자인 회의 식별자 집합
     * @return 슬롯 한 칸의 조회 결과
     */
    private MeetingRoomSlotSummary toSlotSummary(
            LocalTime startTime,
            ReservedSlot reservedSlot,
            Set<Long> attendedMeetingIds
    ) {
        /* 예약 슬롯 행이 없는 시각은 곧 클릭해서 회의를 개설할 수 있는 칸이다. */
        if (reservedSlot == null) {
            return MeetingRoomSlotSummary.available(startTime);
        }

        /* 예약 사실은 공개하되 제목은 참석자에게만 노출한다. */
        return MeetingRoomSlotSummary.reserved(
                startTime,
                reservedSlot.meetingId(),
                reservedSlot.titleFor(attendedMeetingIds.contains(reservedSlot.meetingId()))
        );
    }
}
