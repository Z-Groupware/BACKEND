package com.module06.backend.meetingroom.application.service;

import static java.time.temporal.TemporalAdjusters.next;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailabilitySummary;
import com.module06.backend.meetingroom.application.result.MeetingRoomDayAvailability;
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
 * 조회 대상 회의실, 그 회의실의 평일 5일 예약 슬롯, 요청자의 참석 여부를 각각 한 번씩만 조회하고
 * 날짜별 슬롯 그리드를 조립한다. 날짜 수와 무관하게 조회 횟수가 늘지 않아 N+1이 발생하지 않는다.
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

    /* date 생략 시 서버의 KST 오늘을 결정하며 테스트에서는 고정 시계로 교체한다. */
    private final Clock clock;

    /*
     * 기준일이 속한 주의 단일 회의실 평일 슬롯 현황을 조립해 반환한다.
     *
     * @param query 회사·구성원 식별자와 선택 기준일, 필수 회의실 식별자를 담은 조회 조건
     * @return 단일 회의실의 월요일부터 금요일까지 슬롯 현황
     * @throws BusinessException 요청 회사의 활성 회의실이 아닌 경우
     */
    @Override
    public MeetingRoomAvailability getMeetingRoomAvailability(MeetingRoomAvailabilityQuery query) {
        /* date를 생략한 요청은 시스템 기본 시간대가 아니라 주입된 KST Clock의 오늘을 사용한다. */
        LocalDate referenceDate = query.date() == null ? LocalDate.now(clock) : query.date();

        /* 평일은 해당 주 월요일, 주말은 이미 끝난 주 대신 다음 주 월요일을 조회 시작일로 정한다. */
        LocalDate weekStart = resolveWeekStart(referenceDate);
        LocalDate weekEnd = weekStart.plusDays(4);

        /* 단일 회의실 주간 계약이므로 요청 회사의 활성 회의실 한 곳만 조회한다. */
        MeetingRoom meetingRoom = findTargetMeetingRoom(query);

        /* 월요일 00:00 이상 토요일 00:00 미만의 5일 예약 슬롯을 한 번에 읽는다. */
        List<ReservedSlot> reservedSlots = findReservedSlots(query, meetingRoom, weekStart);

        /* 회의 제목을 노출할 수 있는 회의를 판단하기 위해 요청자가 참석자인 회의 식별자를 조회한다. */
        Set<Long> attendedMeetingIds = findAttendedMeetingIds(query.memberId(), reservedSlots);

        /* 날짜와 시각을 함께 키로 사용해 서로 다른 날의 동일한 시각이 덮어써지지 않게 한다. */
        Map<LocalDate, Map<LocalTime, ReservedSlot>> reservedSlotIndex = indexByDate(reservedSlots);

        /* 월요일부터 금요일까지 순서대로 하루 단위 슬롯 현황을 만든다. */
        List<MeetingRoomDayAvailability> days = IntStream.range(0, 5)
                .mapToObj(offset -> weekStart.plusDays(offset))
                .map(date -> toDayAvailability(
                        date,
                        meetingRoom,
                        reservedSlotIndex.getOrDefault(date, Map.of()),
                        attendedMeetingIds
                ))
                .toList();

        /* 주간 범위와 회의실 메타, 5일 슬롯을 확정된 외부 계약에 맞는 결과로 반환한다. */
        return new MeetingRoomAvailability(
                weekStart,
                weekEnd,
                SlotGrid.SLOT_MINUTES,
                toAvailabilitySummary(meetingRoom),
                days
        );
    }

    /*
     * 평일과 주말 규칙을 적용해 조회 주의 월요일을 계산한다.
     *
     * @param referenceDate 클라이언트가 보냈거나 KST 오늘로 채운 기준일
     * @return 조회 대상 주의 월요일
     */
    private LocalDate resolveWeekStart(LocalDate referenceDate) {
        /* 토요일과 일요일은 지난 주를 보여주지 않고 다음 월요일로 이동한다. */
        if (referenceDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || referenceDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return referenceDate.with(next(DayOfWeek.MONDAY));
        }

        /* 월요일부터 금요일은 자신이 속한 주의 월요일을 포함해 계산한다. */
        return referenceDate.with(previousOrSame(DayOfWeek.MONDAY));
    }

    /*
     * 요청 회사에 속한 활성 회의실 한 곳을 조회한다.
     *
     * @param query 회사와 필수 회의실 식별자를 담은 조회 조건
     * @return 조회 대상 활성 회의실
     * @throws BusinessException 다른 회사·비활성·존재하지 않는 회의실인 경우
     */
    private MeetingRoom findTargetMeetingRoom(MeetingRoomAvailabilityQuery query) {
        /* 타 회사 리소스의 존재를 노출하지 않도록 회사 범위를 포함한 조회 실패를 모두 MR-001로 숨긴다. */
        return meetingRoomRepository.findActiveById(query.companyId(), query.meetingRoomId())
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));
    }

    /*
     * 조회 주의 월요일부터 금요일까지 예약 슬롯을 한 번에 조회한다.
     *
     * @param query 회사 범위를 담은 조회 조건
     * @param meetingRoom 조회 대상 단일 회의실
     * @param weekStart 조회 주의 월요일
     * @return 월요일 00:00 이상 토요일 00:00 미만 예약 슬롯 목록
     */
    private List<ReservedSlot> findReservedSlots(
            MeetingRoomAvailabilityQuery query,
            MeetingRoom meetingRoom,
            LocalDate weekStart
    ) {
        /* 닫힌 시작·열린 종료 범위를 사용해 다음 주 월요일 슬롯이 섞이지 않게 한다. */
        LocalDateTime fromInclusive = weekStart.atStartOfDay();
        LocalDateTime toExclusive = weekStart.plusDays(5).atStartOfDay();

        /* 기존 기간 조회 Port를 단일 회의실 식별자 목록과 5일 범위로 그대로 재사용한다. */
        return meetingRoomSlotRepository.findReservedSlots(
                query.companyId(),
                List.of(meetingRoom.getId()),
                fromInclusive,
                toExclusive
        );
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
     * 예약 슬롯을 날짜와 슬롯 시작 시각으로 색인한다.
     *
     * @param reservedSlots 조회된 예약 슬롯 목록
     * @return 날짜별로 시작 시각을 키로 갖는 예약 슬롯 색인
     */
    private Map<LocalDate, Map<LocalTime, ReservedSlot>> indexByDate(List<ReservedSlot> reservedSlots) {
        /* 단일 회의실 안에서 (날짜, 시각)은 유일하며 날짜를 버리지 않아 5일의 같은 시각을 모두 보존한다. */
        return reservedSlots.stream()
                .collect(Collectors.groupingBy(
                        reservedSlot -> reservedSlot.slotStart().toLocalDate(),
                        Collectors.toMap(ReservedSlot::startTime, Function.identity(), (first, ignored) -> first)
                ));
    }

    /*
     * 조회 대상 회의실의 주간 공통 표시 정보를 조립한다.
     *
     * @param meetingRoom 조립 대상 회의실
     * @return 회의실 식별자와 이름을 담은 결과
     */
    private MeetingRoomAvailabilitySummary toAvailabilitySummary(MeetingRoom meetingRoom) {
        /* 모든 날짜가 공유하는 회의실 메타를 날짜별 결과에 중복하지 않고 상위에 한 번만 둔다. */
        return new MeetingRoomAvailabilitySummary(
                meetingRoom.getId(),
                meetingRoom.getName()
        );
    }

    /*
     * 평일 하루의 슬롯 현황을 조립한다.
     *
     * @param date 조립 대상 날짜
     * @param meetingRoom 조립 대상 회의실
     * @param reservedSlotsByStartTime 해당 날짜의 시작 시각별 예약 슬롯
     * @param attendedMeetingIds 요청자가 참석자인 회의 식별자 집합
     * @return 날짜·요일과 슬롯 현황을 담은 결과
     */
    private MeetingRoomDayAvailability toDayAvailability(
            LocalDate date,
            MeetingRoom meetingRoom,
            Map<LocalTime, ReservedSlot> reservedSlotsByStartTime,
            Set<Long> attendedMeetingIds
    ) {
        /* 슬롯 칸은 00:00부터 23:30까지 하루 전체를 30분 단위로 생성한다. */
        List<MeetingRoomSlotSummary> slots = meetingRoom.slotStartTimes().stream()
                .map(startTime -> toSlotSummary(
                        startTime,
                        reservedSlotsByStartTime.get(startTime),
                        attendedMeetingIds
                ))
                .toList();

        /* 예약이 없는 날도 전체 AVAILABLE 슬롯을 포함해 항상 동일한 그리드 크기를 반환한다. */
        return new MeetingRoomDayAvailability(date, date.getDayOfWeek(), slots);
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
