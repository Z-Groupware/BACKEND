package com.module06.backend.meetingroom.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.application.command.DeactivateMeetingRoomCommand;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomReservationRepository;

/*
 * ROOM-05 회의실 비활성화의 권한·활성 조회·미래 예약·소프트 삭제 규칙을 검증한다.
 */
@DisplayName("ROOM-05 회의실 비활성화 서비스")
class MeetingRoomDeactivationServiceTest {

    /* 예약 조회와 deletedAt의 기준을 2026년 8월 6일 오전 9시 KST로 고정한다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 미래 예약이 없는 활성 회의실이 기존 속성을 유지한 채 비활성화되는지 검증한다. */
    @Test
    @DisplayName("OWNER가 미래 예약 없는 회의실을 소프트 삭제한다")
    void deactivatesMeetingRoomWithoutFutureReservation() {
        /* 활성 회의실과 빈 예약 목록을 반환하는 저장소 대역을 준비한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        RecordingReservationRepository reservationRepository = new RecordingReservationRepository(List.of());
        MeetingRoomDeactivationService service = service(commandRepository, reservationRepository);

        /* 회사 10의 OWNER가 2번 회의실 비활성화를 실행한다. */
        service.deactivateMeetingRoom(command("OWNER"));

        /* 저장된 회의실은 정확한 현재 시각에 비활성화되고 기존 속성을 유지해야 한다. */
        assertThat(commandRepository.savedMeetingRoom).isNotNull();
        assertThat(commandRepository.savedMeetingRoom.isActive()).isFalse();
        assertThat(commandRepository.savedMeetingRoom.getDeletedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 6, 9, 0));
        assertThat(commandRepository.savedMeetingRoom.getName()).isEqualTo("회의실 B");
        assertThat(commandRepository.savedMeetingRoom.getAvailableTo()).isEqualTo(LocalTime.of(18, 0));

        /* 예약 조회에는 토큰 회사·Path ID·동일한 현재 시각이 전달돼야 한다. */
        assertThat(reservationRepository.capturedCompanyId).isEqualTo(10L);
        assertThat(reservationRepository.capturedMeetingRoomId).isEqualTo(2L);
        assertThat(reservationRepository.capturedFrom).isEqualTo(LocalDateTime.of(2026, 8, 6, 9, 0));
    }

    /* ADMIN 역할도 서비스의 관리 권한 검증을 통과하는지 확인한다. */
    @Test
    @DisplayName("ADMIN도 회의실을 비활성화할 수 있다")
    void allowsAdminRole() {
        /* 정상 활성 회의실과 예약 없는 서비스로 ADMIN 요청을 실행한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomDeactivationService service = service(
                commandRepository,
                new RecordingReservationRepository(List.of())
        );

        /* 소문자·공백이 포함된 역할도 토큰 역할 정규화 규칙에 따라 ADMIN으로 처리한다. */
        service.deactivateMeetingRoom(command(" admin "));

        /* 관리 권한을 통과해 비활성 상태가 저장돼야 한다. */
        assertThat(commandRepository.savedMeetingRoom).isNotNull();
        assertThat(commandRepository.savedMeetingRoom.isActive()).isFalse();
    }

    /* 관리 역할이 아닌 내부 호출을 서비스에서도 차단하는지 검증한다. */
    @Test
    @DisplayName("MEMBER의 비활성화 요청은 MR-004로 거절한다")
    void rejectsNonManagementRole() {
        /* 웹 @PreAuthorize를 우회해 서비스를 직접 호출하는 상황을 준비한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomDeactivationService service = service(
                commandRepository,
                new RecordingReservationRepository(List.of())
        );

        /* MEMBER 요청은 회의실 조회나 저장 전에 관리 권한 오류가 발생해야 한다. */
        assertErrorCode(() -> service.deactivateMeetingRoom(command("MEMBER")), "MR-004");
        assertThat(commandRepository.lockCalls).isZero();
        assertThat(commandRepository.savedMeetingRoom).isNull();
    }

    /* 회사 범위의 활성 회의실이 없을 때 존재 여부를 숨기는지 검증한다. */
    @Test
    @DisplayName("타 회사·비활성·미존재 회의실은 MR-001로 거절한다")
    void rejectsMissingActiveMeetingRoom() {
        /* 활성 회의실 잠금 조회 결과가 없는 저장소 대역을 준비한다. */
        RecordingReservationRepository reservationRepository = new RecordingReservationRepository(List.of());
        MeetingRoomDeactivationService service = service(
                new RecordingCommandRepository(null),
                reservationRepository
        );

        /* 정상 OWNER 요청이어도 회사 범위에서 활성 회의실을 찾지 못하면 404 계약이어야 한다. */
        assertErrorCode(() -> service.deactivateMeetingRoom(command("OWNER")), "MR-001");
        assertThat(reservationRepository.calls).isZero();
    }

    /* 미래 SCHEDULED 예약이 남은 회의실을 비활성화하지 않는지 검증한다. */
    @Test
    @DisplayName("미래 SCHEDULED 예약이 있으면 MR-005로 거절한다")
    void rejectsMeetingRoomWithFutureReservation() {
        /* 비활성화 기준 이후에 시작하는 예정 예약 한 건을 준비한다. */
        ScheduledMeetingReservation futureReservation = new ScheduledMeetingReservation(
                LocalDateTime.of(2026, 8, 7, 10, 0),
                LocalDateTime.of(2026, 8, 7, 11, 0)
        );
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomDeactivationService service = service(
                commandRepository,
                new RecordingReservationRepository(List.of(futureReservation))
        );

        /* 예정 예약이 존재하면 MR-005가 발생하고 비활성 상태를 저장하면 안 된다. */
        assertErrorCode(() -> service.deactivateMeetingRoom(command("OWNER")), "MR-005");
        assertThat(commandRepository.savedMeetingRoom).isNull();
    }

    /* 잘못된 인증·경로 값이 저장소 호출 전에 공통 입력 오류가 되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 회사 또는 회의실 식별자는 Z-001로 거절한다")
    void rejectsInvalidIdentifiers() {
        /* 정상 회의실을 가진 서비스라도 0번 회사 요청은 실행할 수 없다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomDeactivationService service = service(
                commandRepository,
                new RecordingReservationRepository(List.of())
        );

        /* 양수가 아닌 인증 회사 식별자는 저장소 경계 전에 거절돼야 한다. */
        DeactivateMeetingRoomCommand invalidCommand = new DeactivateMeetingRoomCommand(0L, "OWNER", 2L);
        assertErrorCode(() -> service.deactivateMeetingRoom(invalidCommand), "Z-001");
        assertThat(commandRepository.lockCalls).isZero();
    }

    /* 테스트 대상 서비스를 고정 Clock과 저장소 대역으로 조립한다. */
    private MeetingRoomDeactivationService service(
            MeetingRoomCommandRepository commandRepository,
            MeetingRoomReservationRepository reservationRepository
    ) {
        /* 운영과 같은 KST이지만 현재 순간이 고정된 서비스를 반환한다. */
        return new MeetingRoomDeactivationService(commandRepository, reservationRepository, FIXED_CLOCK);
    }

    /* 회사 10의 2번 회의실을 대상으로 역할별 비활성화 명령을 만든다. */
    private DeactivateMeetingRoomCommand command(String role) {
        /* Access Token에서 가져올 회사와 역할, Path 식별자를 명령에 담는다. */
        return new DeactivateMeetingRoomCommand(10L, role, 2L);
    }

    /* 테스트에서 비활성화할 기존 활성 회의실을 만든다. */
    private MeetingRoom existingRoom() {
        /* 회사·표시·운영 속성과 식별자를 가진 활성 회의실이다. */
        return new MeetingRoom(
                2L,
                10L,
                "회의실 B",
                "박애관 422호",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null
        );
    }

    /* 실행 결과가 특정 공개 비즈니스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* 예외 타입과 ErrorCode 문자열을 함께 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 비활성화 서비스가 실행한 잠금 조회와 저장 요청을 기록하는 명령 저장소 대역이다. */
    private static final class RecordingCommandRepository implements MeetingRoomCommandRepository {

        /* 잠금 조회에서 반환할 활성 회의실이다. */
        private final MeetingRoom currentMeetingRoom;

        /* 활성 회의실 잠금 조회 호출 횟수다. */
        private int lockCalls;

        /* 저장 요청에 전달된 비활성 회의실 상태다. */
        private MeetingRoom savedMeetingRoom;

        /* 테스트별 잠금 조회 결과로 명령 저장소 대역을 만든다. */
        private RecordingCommandRepository(MeetingRoom currentMeetingRoom) {
            /* null이면 회사 범위에서 활성 회의실을 찾지 못한 상황이다. */
            this.currentMeetingRoom = currentMeetingRoom;
        }

        /* ROOM-03 이름 중복 조회는 ROOM-05 테스트에서 사용하지 않는다. */
        @Override
        public boolean existsActiveByCompanyIdAndName(Long companyId, String name) {
            /* 호출되지 않는 등록 계약이므로 중복이 없다고 반환한다. */
            return false;
        }

        /* 비활성화 대상 활성 회의실의 잠금 조회 결과를 반환한다. */
        @Override
        public Optional<MeetingRoom> findActiveByIdForUpdate(Long companyId, Long meetingRoomId) {
            /* 호출을 기록하고 준비된 활성 회의실을 Optional로 반환한다. */
            lockCalls++;
            return Optional.ofNullable(currentMeetingRoom);
        }

        /* ROOM-04 자기 자신 제외 이름 조회는 ROOM-05 테스트에서 사용하지 않는다. */
        @Override
        public boolean existsActiveByCompanyIdAndNameExcludingId(
                Long companyId,
                String name,
                Long excludedMeetingRoomId
        ) {
            /* 호출되지 않는 수정 계약이므로 중복이 없다고 반환한다. */
            return false;
        }

        /* 비활성화된 최종 회의실 상태를 기록하고 그대로 반환한다. */
        @Override
        public MeetingRoom save(MeetingRoom meetingRoom) {
            /* 실제 DB merge 대신 전달된 소프트 삭제 상태를 메모리에 보관한다. */
            this.savedMeetingRoom = meetingRoom;
            return meetingRoom;
        }
    }

    /* 미래 예약 조회 조건과 호출 횟수를 기록하는 저장소 대역이다. */
    private static final class RecordingReservationRepository implements MeetingRoomReservationRepository {

        /* 조회 시 반환할 미래 SCHEDULED 예약 목록이다. */
        private final List<ScheduledMeetingReservation> reservations;

        /* 미래 예약 조회 호출 횟수다. */
        private int calls;

        /* 예약 조회에 전달된 회사 식별자다. */
        private Long capturedCompanyId;

        /* 예약 조회에 전달된 회의실 식별자다. */
        private Long capturedMeetingRoomId;

        /* 예약 조회에 전달된 현재 시각이다. */
        private LocalDateTime capturedFrom;

        /* 테스트별 반환 예약 목록으로 예약 저장소 대역을 만든다. */
        private RecordingReservationRepository(List<ScheduledMeetingReservation> reservations) {
            /* 외부 변경이 테스트 결과에 영향을 주지 않도록 목록을 복사한다. */
            this.reservations = new ArrayList<>(reservations);
        }

        /* 조회 조건을 기록하고 준비된 미래 예약 목록을 반환한다. */
        @Override
        public List<ScheduledMeetingReservation> findFutureScheduledReservations(
                Long companyId,
                Long meetingRoomId,
                LocalDateTime fromInclusive
        ) {
            /* 테넌트·회의실·시간 범위가 정확한지 검증할 수 있도록 호출 정보를 기록한다. */
            calls++;
            capturedCompanyId = companyId;
            capturedMeetingRoomId = meetingRoomId;
            capturedFrom = fromInclusive;
            return List.copyOf(reservations);
        }
    }
}
