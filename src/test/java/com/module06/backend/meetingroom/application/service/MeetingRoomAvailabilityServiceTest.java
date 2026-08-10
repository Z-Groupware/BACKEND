package com.module06.backend.meetingroom.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomSlotSummary;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.MeetingRoomSlotStatus;
import com.module06.backend.meetingroom.domain.model.ReservedSlot;
import com.module06.backend.meetingroom.domain.repository.MeetingAttendanceRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomSlotRepository;

/*
 * MeetingRoomAvailabilityService의 ROOM-02 애플리케이션 로직을 검증하는 단위 테스트다.
 *
 * Spring과 JPA를 실행하지 않고 도메인 저장소 대역을 사용해
 * 슬롯 상태 조립, 제목 마스킹, 회의실 필터, 조회 범위 계약을 빠르게 검증한다.
 */
@DisplayName("ROOM-02 회의실 예약 현황 조회 서비스")
class MeetingRoomAvailabilityServiceTest {

    /* 테스트에서 사용하는 요청자의 회사 식별자다. */
    private static final Long COMPANY_ID = 10L;

    /* 테스트에서 사용하는 요청자의 구성원 식별자다. */
    private static final Long MEMBER_ID = 3L;

    /* 테스트에서 사용하는 조회 날짜다. */
    private static final LocalDate DATE = LocalDate.of(2026, 8, 4);

    /*
     * 예약이 없는 슬롯과 예약된 슬롯이 이용 가능 시간 안에서만 만들어지는지 검증한다.
     */
    @Test
    @DisplayName("이용 가능 시간 안의 슬롯만 만들고 예약 여부로 상태를 채운다")
    void buildsSlotsOnlyInsideAvailableTime() {
        /* 09:00~11:00 회의실 한 곳과 14:00 예약(이용 시간 밖)·09:30 예약을 준비한다. */
        MeetingRoom meetingRoom = meetingRoom(1L, "회의실 A", LocalTime.of(9, 0), LocalTime.of(11, 0));
        FakeMeetingRoomSlotRepository slotRepository = new FakeMeetingRoomSlotRepository(List.of(
                new ReservedSlot(1L, LocalDateTime.of(2026, 8, 4, 9, 30), 91L, "온보딩 킥오프"),
                new ReservedSlot(1L, LocalDateTime.of(2026, 8, 4, 14, 0), 91L, "온보딩 킥오프")
        ));
        MeetingRoomAvailabilityService service = new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(List.of(meetingRoom)),
                slotRepository,
                (memberId, meetingIds) -> Set.of(91L)
        );

        /* 회의실 필터 없이 하루 현황을 조회한다. */
        MeetingRoomAvailability availability = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, MEMBER_ID, DATE, null)
        );

        /* 응답 헤더 값이 조회 조건과 도메인 상수를 그대로 따르는지 확인한다. */
        assertThat(availability.date()).isEqualTo(DATE);
        assertThat(availability.slotMinutes()).isEqualTo(30);

        /* 이용 시간 밖의 14:00 예약은 칸이 없으므로 09:00~10:30의 4칸만 나와야 한다. */
        List<MeetingRoomSlotSummary> slots = availability.meetingRooms().get(0).slots();
        assertThat(slots).hasSize(4);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(0).status()).isEqualTo(MeetingRoomSlotStatus.AVAILABLE);
        assertThat(slots.get(0).meetingId()).isNull();
        assertThat(slots.get(0).title()).isNull();

        /* 예약된 09:30 칸은 회의 식별자와 제목을 함께 노출해야 한다. */
        assertThat(slots.get(1).status()).isEqualTo(MeetingRoomSlotStatus.RESERVED);
        assertThat(slots.get(1).meetingId()).isEqualTo(91L);
        assertThat(slots.get(1).title()).isEqualTo("온보딩 킥오프");
    }

    /*
     * 참석자가 아닌 회의의 제목이 마스킹되는지 검증한다.
     */
    @Test
    @DisplayName("참석자가 아닌 회의는 제목을 숨기고 예약 상태만 노출한다")
    void masksTitleWhenRequesterIsNotAttendee() {
        /* 요청자가 91번 회의에만 참석자로 등록된 상황을 준비한다. */
        MeetingRoom meetingRoom = meetingRoom(1L, "회의실 A", LocalTime.of(9, 0), LocalTime.of(10, 0));
        MeetingRoomAvailabilityService service = new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(List.of(meetingRoom)),
                new FakeMeetingRoomSlotRepository(List.of(
                        new ReservedSlot(1L, LocalDateTime.of(2026, 8, 4, 9, 0), 91L, "참석 중인 회의"),
                        new ReservedSlot(1L, LocalDateTime.of(2026, 8, 4, 9, 30), 94L, "다른 팀 회의")
                )),
                (memberId, meetingIds) -> Set.of(91L)
        );

        /* 하루 현황을 조회한다. */
        MeetingRoomAvailability availability = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, MEMBER_ID, DATE, null)
        );

        /* 참석 회의는 제목이 보이고, 참석하지 않은 회의는 제목만 비어야 한다. */
        List<MeetingRoomSlotSummary> slots = availability.meetingRooms().get(0).slots();
        assertThat(slots.get(0).title()).isEqualTo("참석 중인 회의");
        assertThat(slots.get(1).status()).isEqualTo(MeetingRoomSlotStatus.RESERVED);
        assertThat(slots.get(1).meetingId()).isEqualTo(94L);
        assertThat(slots.get(1).title()).isNull();
    }

    /*
     * 요청자를 특정할 수 없으면 참석 조회 없이 모든 제목이 마스킹되는지 검증한다.
     */
    @Test
    @DisplayName("구성원 식별자가 없으면 참석 조회 없이 모든 제목을 숨긴다")
    void masksAllTitlesWhenMemberIdIsMissing() {
        /* 참석 조회가 호출되면 실패하도록 대역을 준비한다. */
        MeetingRoom meetingRoom = meetingRoom(1L, "회의실 A", LocalTime.of(9, 0), LocalTime.of(9, 30));
        MeetingAttendanceRepository attendanceRepository = (memberId, meetingIds) -> {
            throw new AssertionError("요청자를 특정할 수 없으면 참석 여부를 조회하지 않아야 한다.");
        };
        MeetingRoomAvailabilityService service = new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(List.of(meetingRoom)),
                new FakeMeetingRoomSlotRepository(List.of(
                        new ReservedSlot(1L, LocalDateTime.of(2026, 8, 4, 9, 0), 91L, "온보딩 킥오프")
                )),
                attendanceRepository
        );

        /* 구성원 식별자 없이 조회한다. */
        MeetingRoomAvailability availability = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, null, DATE, null)
        );

        /* 예약 사실만 남고 제목은 노출되지 않아야 한다. */
        assertThat(availability.meetingRooms().get(0).slots().get(0).status())
                .isEqualTo(MeetingRoomSlotStatus.RESERVED);
        assertThat(availability.meetingRooms().get(0).slots().get(0).title()).isNull();
    }

    /*
     * 회의실 필터를 넘겼을 때 단건 조회와 하루 범위 조회 조건이 사용되는지 검증한다.
     */
    @Test
    @DisplayName("회의실 필터가 있으면 해당 회의실만 하루 범위로 조회한다")
    void queriesSingleMeetingRoomForRequestedDay() {
        /* 회사에 두 회의실이 있는 상황에서 2번 회의실만 조회하도록 준비한다. */
        MeetingRoom first = meetingRoom(1L, "회의실 A", LocalTime.of(9, 0), LocalTime.of(10, 0));
        MeetingRoom second = meetingRoom(2L, "회의실 B", LocalTime.of(9, 0), LocalTime.of(10, 0));
        FakeMeetingRoomSlotRepository slotRepository = new FakeMeetingRoomSlotRepository(List.of());
        MeetingRoomAvailabilityService service = new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(List.of(first, second)),
                slotRepository,
                (memberId, meetingIds) -> Set.of()
        );

        /* 특정 회의실만 조회한다. */
        MeetingRoomAvailability availability = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, MEMBER_ID, DATE, 2L)
        );

        /* 응답에 지정한 회의실만 담기는지 확인한다. */
        assertThat(availability.meetingRooms()).hasSize(1);
        assertThat(availability.meetingRooms().get(0).meetingRoomId()).isEqualTo(2L);

        /* 슬롯 조회가 해당 회의실과 당일 00:00 이상 다음 날 00:00 미만 범위로 실행됐는지 확인한다. */
        assertThat(slotRepository.requestedCompanyId).isEqualTo(COMPANY_ID);
        assertThat(slotRepository.requestedMeetingRoomIds).containsExactly(2L);
        assertThat(slotRepository.requestedFromInclusive).isEqualTo(LocalDateTime.of(2026, 8, 4, 0, 0));
        assertThat(slotRepository.requestedToExclusive).isEqualTo(LocalDateTime.of(2026, 8, 5, 0, 0));
    }

    /*
     * 다른 회사이거나 존재하지 않는 회의실을 지정하면 404로 응답하는지 검증한다.
     */
    @Test
    @DisplayName("요청 회사의 활성 회의실이 아니면 MR-001로 거절한다")
    void rejectsUnknownMeetingRoom() {
        /* 조회 결과가 없는 저장소 대역으로 서비스를 생성한다. */
        MeetingRoomAvailabilityService service = new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(List.of()),
                new FakeMeetingRoomSlotRepository(List.of()),
                (memberId, meetingIds) -> Set.of()
        );

        /* 존재하지 않는 회의실을 지정해 조회한다. */
        assertThatThrownBy(() -> service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, MEMBER_ID, DATE, 99L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MR-001");
    }

    /*
     * 활성 회의실이 없을 때 예외 없이 빈 목록이 반환되는지 검증한다.
     */
    @Test
    @DisplayName("활성 회의실이 없으면 슬롯 조회 없이 빈 목록을 반환한다")
    void returnsEmptyMeetingRoomsWhenCompanyHasNoMeetingRoom() {
        /* 슬롯 조회가 호출되면 실패하도록 대역을 준비한다. */
        MeetingRoomSlotRepository slotRepository = (companyId, meetingRoomIds, from, to) -> {
            throw new AssertionError("조회 대상 회의실이 없으면 슬롯을 조회하지 않아야 한다.");
        };
        MeetingRoomAvailabilityService service = new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(List.of()),
                slotRepository,
                (memberId, meetingIds) -> Set.of()
        );

        /* 회의실이 없는 회사의 현황을 조회한다. */
        MeetingRoomAvailability availability = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, MEMBER_ID, DATE, null)
        );

        /* 404가 아니라 빈 배열을 만들 수 있도록 빈 목록이 반환되는지 확인한다. */
        assertThat(availability.meetingRooms()).isEmpty();
    }

    /*
     * 테스트용 회의실 도메인 객체를 생성한다.
     *
     * @param id 회의실 식별자
     * @param name 회의실 이름
     * @param availableFrom 이용 가능 시작 시각
     * @param availableTo 이용 가능 종료 시각
     * @return 활성 상태의 회의실 도메인 객체
     */
    private MeetingRoom meetingRoom(Long id, String name, LocalTime availableFrom, LocalTime availableTo) {
        /* 슬롯 계산에 필요한 값만 채우고 비활성화 시각은 비워 활성 회의실로 만든다. */
        return new MeetingRoom(id, COMPANY_ID, name, "박애관 421호", availableFrom, availableTo, null);
    }

    /*
     * 준비된 회의실 목록을 반환하는 저장소 대역이다.
     */
    private static final class FakeMeetingRoomRepository implements MeetingRoomRepository {

        /* 서비스에 반환할 회의실 목록이다. */
        private final List<MeetingRoom> meetingRooms;

        /*
         * 저장소 대역이 반환할 회의실 목록을 설정한다.
         *
         * @param meetingRooms 서비스에 반환할 회의실 목록
         */
        private FakeMeetingRoomRepository(List<MeetingRoom> meetingRooms) {
            /* 테스트 중 목록이 바뀌지 않도록 불변 복사본을 저장한다. */
            this.meetingRooms = List.copyOf(meetingRooms);
        }

        /*
         * 준비된 회의실 목록을 그대로 반환한다.
         *
         * @param companyId 서비스가 전달한 회사 식별자
         * @return 테스트에서 준비한 회의실 목록
         */
        @Override
        public List<MeetingRoom> findAllActiveByCompanyId(Long companyId) {
            /* 실제 DB 조회 대신 준비된 목록을 반환한다. */
            return meetingRooms;
        }

        /*
         * 준비된 목록에서 식별자가 일치하는 회의실을 반환한다.
         *
         * @param companyId 서비스가 전달한 회사 식별자
         * @param meetingRoomId 서비스가 전달한 회의실 식별자
         * @return 식별자가 일치하는 회의실, 없으면 빈 Optional
         */
        @Override
        public Optional<MeetingRoom> findActiveById(Long companyId, Long meetingRoomId) {
            /* 다른 회사·비활성 회의실을 조회하지 못한 상황은 빈 Optional로 표현한다. */
            return meetingRooms.stream()
                    .filter(meetingRoom -> meetingRoom.getId().equals(meetingRoomId))
                    .findFirst();
        }

        /* MEET-03 배치 조회는 ROOM-02 서비스 테스트에서 사용하지 않는다. */
        @Override
        public List<MeetingRoom> findAllByIds(Long companyId, List<Long> meetingRoomIds) {
            /* 계약을 만족시키면서 요청 식별자에 포함된 준비 회의실만 반환한다. */
            return meetingRooms.stream()
                    .filter(meetingRoom -> meetingRoomIds.contains(meetingRoom.getId()))
                    .toList();
        }
    }

    /*
     * 준비된 예약 슬롯을 반환하고 전달된 조회 조건을 기록하는 저장소 대역이다.
     */
    private static final class FakeMeetingRoomSlotRepository implements MeetingRoomSlotRepository {

        /* 서비스에 반환할 예약 슬롯 목록이다. */
        private final List<ReservedSlot> reservedSlots;

        /* 서비스가 전달한 회사 식별자를 기록한다. */
        private Long requestedCompanyId;

        /* 서비스가 전달한 회의실 식별자 목록을 기록한다. */
        private List<Long> requestedMeetingRoomIds = new ArrayList<>();

        /* 서비스가 전달한 조회 시작 일시를 기록한다. */
        private LocalDateTime requestedFromInclusive;

        /* 서비스가 전달한 조회 종료 일시를 기록한다. */
        private LocalDateTime requestedToExclusive;

        /*
         * 저장소 대역이 반환할 예약 슬롯 목록을 설정한다.
         *
         * @param reservedSlots 서비스에 반환할 예약 슬롯 목록
         */
        private FakeMeetingRoomSlotRepository(List<ReservedSlot> reservedSlots) {
            /* 테스트 중 목록이 바뀌지 않도록 불변 복사본을 저장한다. */
            this.reservedSlots = List.copyOf(reservedSlots);
        }

        /*
         * 전달된 조회 조건을 기록하고 준비된 슬롯 목록을 반환한다.
         *
         * @param companyId 서비스가 전달한 회사 식별자
         * @param meetingRoomIds 서비스가 전달한 회의실 식별자 목록
         * @param fromInclusive 서비스가 전달한 조회 시작 일시
         * @param toExclusive 서비스가 전달한 조회 종료 일시
         * @return 테스트에서 준비한 예약 슬롯 목록
         */
        @Override
        public List<ReservedSlot> findReservedSlots(
                Long companyId,
                List<Long> meetingRoomIds,
                LocalDateTime fromInclusive,
                LocalDateTime toExclusive
        ) {
            /* 회사 범위와 조회 기간 계약을 검증할 수 있도록 요청값을 저장한다. */
            this.requestedCompanyId = companyId;
            this.requestedMeetingRoomIds = List.copyOf(meetingRoomIds);
            this.requestedFromInclusive = fromInclusive;
            this.requestedToExclusive = toExclusive;

            /* 실제 DB 조회 대신 준비된 슬롯 목록을 반환한다. */
            return reservedSlots;
        }
    }
}
