package com.module06.backend.meetingroom.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomDayAvailability;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.MeetingRoomSlotStatus;
import com.module06.backend.meetingroom.domain.model.ReservedSlot;
import com.module06.backend.meetingroom.domain.repository.MeetingAttendanceRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomSlotRepository;

/*
 * MeetingRoomAvailabilityService의 단일 회의실 주간 조회 계약을 검증하는 단위 테스트다.
 */
@DisplayName("ROOM-02 회의실 주간 예약 현황 조회 서비스")
class MeetingRoomAvailabilityServiceTest {

    /* 테스트 요청자의 회사 식별자다. */
    private static final Long COMPANY_ID = 10L;

    /* 테스트 요청자의 구성원 식별자다. */
    private static final Long MEMBER_ID = 3L;

    /* date 생략과 주말 계산을 결정적으로 검증하기 위한 일요일 KST 고정 시계다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-09T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 평일 기준일의 월요일부터 금요일까지 슬롯을 만들고 같은 시각의 날짜별 예약을 보존하는지 검증한다. */
    @Test
    @DisplayName("평일 기준일이 속한 주의 월요일부터 금요일까지 날짜별 슬롯을 반환한다")
    void returnsWeekdaySlotsWithoutOverwritingSameTimes() {
        /* 월요일과 화요일의 동일한 09:00 예약 및 이용 시간 밖 예약을 준비한다. */
        MeetingRoom meetingRoom = meetingRoom(2L, LocalTime.of(9, 0), LocalTime.of(10, 0));
        FakeMeetingRoomSlotRepository slotRepository = new FakeMeetingRoomSlotRepository(List.of(
                new ReservedSlot(2L, LocalDateTime.of(2026, 8, 10, 9, 0), 91L, "월요일 회의"),
                new ReservedSlot(2L, LocalDateTime.of(2026, 8, 11, 9, 0), 92L, "화요일 회의"),
                new ReservedSlot(2L, LocalDateTime.of(2026, 8, 12, 14, 0), 93L, "이용 시간 밖 회의")
        ));
        MeetingRoomAvailabilityService service = service(
                List.of(meetingRoom),
                slotRepository,
                (memberId, meetingIds) -> Set.of(91L, 92L, 93L)
        );

        /* 주중 수요일을 기준일로 단일 회의실의 주간 현황을 조회한다. */
        MeetingRoomAvailability result = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(
                        COMPANY_ID,
                        MEMBER_ID,
                        LocalDate.of(2026, 8, 12),
                        2L
                )
        );

        /* 주간 헤더와 단일 회의실 정보가 확정 계약대로 조립되는지 확인한다. */
        assertThat(result.weekStart()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(result.weekEnd()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(result.slotMinutes()).isEqualTo(30);
        assertThat(result.meetingRoom().meetingRoomId()).isEqualTo(2L);
        assertThat(result.days()).extracting(MeetingRoomDayAvailability::date)
                .containsExactly(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 11),
                        LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 13),
                        LocalDate.of(2026, 8, 14)
                );

        /* 월요일과 화요일의 같은 시각 예약이 서로 덮어써지지 않는지 확인한다. */
        assertThat(result.days().get(0).slots().get(0).meetingId()).isEqualTo(91L);
        assertThat(result.days().get(0).slots().get(0).title()).isEqualTo("월요일 회의");
        assertThat(result.days().get(1).slots().get(0).meetingId()).isEqualTo(92L);
        assertThat(result.days().get(1).slots().get(0).title()).isEqualTo("화요일 회의");

        /* 이용 시간 밖에는 칸을 만들지 않고 예약이 없는 날도 전체 AVAILABLE 칸을 제공한다. */
        assertThat(result.days()).allSatisfy(day -> assertThat(day.slots()).hasSize(2));
        assertThat(result.days().get(2).slots()).allSatisfy(slot ->
                assertThat(slot.status()).isEqualTo(MeetingRoomSlotStatus.AVAILABLE));

        /* 저장소가 단일 회의실과 월요일 00:00 이상 토요일 00:00 미만 범위로 한 번 호출됐는지 확인한다. */
        assertThat(slotRepository.requestedCompanyId).isEqualTo(COMPANY_ID);
        assertThat(slotRepository.requestedMeetingRoomIds).containsExactly(2L);
        assertThat(slotRepository.requestedFromInclusive)
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 0, 0));
        assertThat(slotRepository.requestedToExclusive)
                .isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0));
    }

    /* 토요일과 일요일 기준일이 모두 다음 주 월요일로 이동하는지 검증한다. */
    @Test
    @DisplayName("주말 기준일은 다음 주 월요일부터 조회한다")
    void movesWeekendReferenceDateToNextMonday() {
        /* 예약이 없는 회의실과 저장소 대역을 준비한다. */
        MeetingRoomAvailabilityService service = service(
                List.of(meetingRoom(2L, LocalTime.of(9, 0), LocalTime.of(10, 0))),
                new FakeMeetingRoomSlotRepository(List.of()),
                (memberId, meetingIds) -> Set.of()
        );

        /* 같은 주말의 토요일과 일요일을 각각 조회한다. */
        MeetingRoomAvailability saturday = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(
                        COMPANY_ID, MEMBER_ID, LocalDate.of(2026, 8, 8), 2L));
        MeetingRoomAvailability sunday = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(
                        COMPANY_ID, MEMBER_ID, LocalDate.of(2026, 8, 9), 2L));

        /* 두 요청 모두 다음 주 월요일인 8월 10일부터 시작해야 한다. */
        assertThat(saturday.weekStart()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(sunday.weekStart()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    /* date 생략 시 시스템 기본 시간대가 아닌 주입된 KST Clock을 사용하는지 검증한다. */
    @Test
    @DisplayName("date를 생략하면 KST 오늘을 기준으로 조회 주를 계산한다")
    void usesKoreanTodayWhenDateIsMissing() {
        /* KST 기준 일요일인 고정 시계와 예약이 없는 회의실을 준비한다. */
        MeetingRoomAvailabilityService service = service(
                List.of(meetingRoom(2L, LocalTime.of(9, 0), LocalTime.of(10, 0))),
                new FakeMeetingRoomSlotRepository(List.of()),
                (memberId, meetingIds) -> Set.of()
        );

        /* 기준일을 null로 전달해 서버 기본 날짜 경로를 실행한다. */
        MeetingRoomAvailability result = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(COMPANY_ID, MEMBER_ID, null, 2L)
        );

        /* 고정 시계의 일요일 다음 날인 8월 10일이 조회 주의 시작이어야 한다. */
        assertThat(result.weekStart()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    /* 참석자가 아닌 예약은 제목만 숨기고 예약 상태와 회의 식별자는 유지하는지 검증한다. */
    @Test
    @DisplayName("참석자가 아닌 회의는 제목을 숨기고 예약 상태만 노출한다")
    void masksMeetingTitleForNonAttendee() {
        /* 요청자가 참석하지 않은 월요일 예약을 준비한다. */
        MeetingRoomAvailabilityService service = service(
                List.of(meetingRoom(2L, LocalTime.of(9, 0), LocalTime.of(9, 30))),
                new FakeMeetingRoomSlotRepository(List.of(
                        new ReservedSlot(2L, LocalDateTime.of(2026, 8, 10, 9, 0), 94L, "비공개 회의")
                )),
                (memberId, meetingIds) -> Set.of()
        );

        /* 월요일을 기준으로 주간 현황을 조회한다. */
        MeetingRoomAvailability result = service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(
                        COMPANY_ID, MEMBER_ID, LocalDate.of(2026, 8, 10), 2L)
        );

        /* 예약 상태와 식별자는 유지하되 제목만 null이어야 한다. */
        assertThat(result.days().get(0).slots().get(0).status())
                .isEqualTo(MeetingRoomSlotStatus.RESERVED);
        assertThat(result.days().get(0).slots().get(0).meetingId()).isEqualTo(94L);
        assertThat(result.days().get(0).slots().get(0).title()).isNull();
    }

    /* 타 회사·비활성·존재하지 않는 회의실을 MR-001로 숨기는지 검증한다. */
    @Test
    @DisplayName("요청 회사의 활성 회의실이 아니면 MR-001로 거절한다")
    void rejectsUnknownMeetingRoom() {
        /* 활성 회의실이 없는 저장소로 서비스를 준비한다. */
        MeetingRoomAvailabilityService service = service(
                List.of(),
                new FakeMeetingRoomSlotRepository(List.of()),
                (memberId, meetingIds) -> Set.of()
        );

        /* 조회할 수 없는 회의실을 요청하면 테넌트 범위 404가 발생해야 한다. */
        assertThatThrownBy(() -> service.getMeetingRoomAvailability(
                new MeetingRoomAvailabilityQuery(
                        COMPANY_ID, MEMBER_ID, LocalDate.of(2026, 8, 10), 99L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MR-001");
    }

    /* 테스트 대상 서비스를 고정 KST Clock과 저장소 대역으로 조립한다. */
    private MeetingRoomAvailabilityService service(
            List<MeetingRoom> meetingRooms,
            MeetingRoomSlotRepository slotRepository,
            MeetingAttendanceRepository attendanceRepository
    ) {
        /* 운영과 동일한 의존성 방향을 유지하면서 외부 입출력만 메모리 대역으로 교체한다. */
        return new MeetingRoomAvailabilityService(
                new FakeMeetingRoomRepository(meetingRooms),
                slotRepository,
                attendanceRepository,
                FIXED_CLOCK
        );
    }

    /* 테스트에 필요한 활성 회의실 도메인 객체를 생성한다. */
    private MeetingRoom meetingRoom(Long id, LocalTime availableFrom, LocalTime availableTo) {
        /* 위치와 이용 가능 시각을 포함하고 비활성화 시각은 비워 활성 회의실을 만든다. */
        return new MeetingRoom(id, COMPANY_ID, "회의실 B", "박애관 421호", availableFrom, availableTo, null);
    }

    /* 준비된 회의실 목록에서 단건 조회를 수행하는 저장소 대역이다. */
    private static final class FakeMeetingRoomRepository implements MeetingRoomRepository {

        /* 서비스에 제공할 활성 회의실 목록이다. */
        private final List<MeetingRoom> meetingRooms;

        /* 테스트 중 변경되지 않도록 준비 목록을 불변 복사한다. */
        private FakeMeetingRoomRepository(List<MeetingRoom> meetingRooms) {
            /* 외부 가변 목록이 테스트 결과에 영향을 주지 않게 한다. */
            this.meetingRooms = List.copyOf(meetingRooms);
        }

        /* ROOM-01용 전체 조회는 이번 서비스 테스트에서 사용하지 않는다. */
        @Override
        public List<MeetingRoom> findAllActiveByCompanyId(Long companyId) {
            /* 계약을 만족시키기 위해 준비된 목록을 반환한다. */
            return meetingRooms;
        }

        /* 요청 식별자와 일치하는 활성 회의실 하나를 반환한다. */
        @Override
        public Optional<MeetingRoom> findActiveById(Long companyId, Long meetingRoomId) {
            /* 준비 목록에서 식별자가 같은 회의실만 조회한다. */
            return meetingRooms.stream()
                    .filter(meetingRoom -> meetingRoom.getId().equals(meetingRoomId))
                    .findFirst();
        }

        /* 다른 조회 기능의 배치 계약은 ROOM-02 테스트에서 사용하지 않는다. */
        @Override
        public List<MeetingRoom> findAllByIds(Long companyId, List<Long> meetingRoomIds) {
            /* 요청 식별자에 포함된 준비 회의실만 반환한다. */
            return meetingRooms.stream()
                    .filter(meetingRoom -> meetingRoomIds.contains(meetingRoom.getId()))
                    .toList();
        }
    }

    /* 준비된 예약 슬롯을 반환하고 서비스의 조회 범위를 기록하는 저장소 대역이다. */
    private static final class FakeMeetingRoomSlotRepository implements MeetingRoomSlotRepository {

        /* 서비스에 반환할 예약 슬롯 목록이다. */
        private final List<ReservedSlot> reservedSlots;

        /* 전달받은 회사 식별자다. */
        private Long requestedCompanyId;

        /* 전달받은 회의실 식별자 목록이다. */
        private List<Long> requestedMeetingRoomIds = new ArrayList<>();

        /* 전달받은 조회 시작 일시다. */
        private LocalDateTime requestedFromInclusive;

        /* 전달받은 조회 종료 일시다. */
        private LocalDateTime requestedToExclusive;

        /* 테스트 중 슬롯 목록이 바뀌지 않도록 불변 복사한다. */
        private FakeMeetingRoomSlotRepository(List<ReservedSlot> reservedSlots) {
            /* 외부 가변 목록이 저장소 대역 결과를 변경하지 않게 한다. */
            this.reservedSlots = List.copyOf(reservedSlots);
        }

        /* 조회 조건을 기록한 뒤 준비된 예약 슬롯을 반환한다. */
        @Override
        public List<ReservedSlot> findReservedSlots(
                Long companyId,
                List<Long> meetingRoomIds,
                LocalDateTime fromInclusive,
                LocalDateTime toExclusive
        ) {
            /* 조회 범위 계약을 테스트에서 검증할 수 있도록 모든 인자를 보관한다. */
            this.requestedCompanyId = companyId;
            this.requestedMeetingRoomIds = List.copyOf(meetingRoomIds);
            this.requestedFromInclusive = fromInclusive;
            this.requestedToExclusive = toExclusive;

            /* 실제 DB 조회 대신 준비된 슬롯 목록을 반환한다. */
            return reservedSlots;
        }
    }
}
