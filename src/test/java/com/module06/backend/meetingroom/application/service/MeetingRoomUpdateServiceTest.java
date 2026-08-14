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
import com.module06.backend.meetingroom.application.command.UpdateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.model.ScheduledMeetingReservation;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomReservationRepository;

/*
 * ROOM-04 회의실 이름·위치 부분 수정의 병합·권한·중복 규칙을 검증한다.
 */
@DisplayName("ROOM-04 회의실 수정 서비스")
class MeetingRoomUpdateServiceTest {

    /* 미래 예약 판정 기준을 2026년 8월 6일 오전 9시 KST로 고정한다. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /* 전달된 필드만 바뀌고 나머지 값은 유지되는지 검증한다. */
    @Test
    @DisplayName("일부 필드만 수정하고 저장된 전체 상태를 반환한다")
    void updatesOnlyProvidedFields() {
        /* 기존 회의실과 예약 없는 저장소 대역으로 수정 서비스를 준비한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomUpdateService service = service(commandRepository);

        /* 위치만 바꾸는 OWNER 요청을 실행한다. */
        UpdateMeetingRoomCommand command = command(
                "OWNER",
                false, null,
                true, "본관 3층"
        );
        MeetingRoomUpdateResult result = service.updateMeetingRoom(command);

        /* 요청한 위치만 바뀌고 이름은 기존 상태를 유지해야 한다. */
        assertThat(result.meetingRoomId()).isEqualTo(2L);
        assertThat(result.name()).isEqualTo("회의실 B");
        assertThat(result.location()).isEqualTo("본관 3층");
    }

    /* location 명시적 null이 기존 위치 삭제로 반영되는지 검증한다. */
    @Test
    @DisplayName("location을 null로 전달하면 기존 위치를 삭제한다")
    void clearsLocationWhenExplicitNullProvided() {
        /* 기존 위치가 존재하는 회의실과 수정 서비스를 준비한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomUpdateService service = service(commandRepository);

        /* locationProvided만 true이고 값은 null인 ADMIN 요청을 실행한다. */
        UpdateMeetingRoomCommand command = command(
                "ADMIN",
                false, null,
                true, null
        );
        MeetingRoomUpdateResult result = service.updateMeetingRoom(command);

        /* 기존 위치가 유지되지 않고 명시적으로 삭제돼야 한다. */
        assertThat(result.location()).isNull();
        assertThat(commandRepository.savedMeetingRoom.getLocation()).isNull();
    }

    /* 수정 필드가 없는 요청을 저장 전에 거절하는지 검증한다. */
    @Test
    @DisplayName("빈 PATCH 요청은 Z-001로 거절한다")
    void rejectsEmptyPatchRequest() {
        /* 정상 회의실을 가진 수정 서비스를 준비한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        MeetingRoomUpdateService service = service(commandRepository);

        /* 모든 provided 플래그가 false인 요청은 공통 입력 오류여야 한다. */
        UpdateMeetingRoomCommand command = command(
                "OWNER",
                false, null,
                false, null
        );
        assertErrorCode(() -> service.updateMeetingRoom(command), "Z-001");
        assertThat(commandRepository.savedMeetingRoom).isNull();
    }

    /* 관리 역할이 아닌 내부 호출을 서비스에서도 차단하는지 검증한다. */
    @Test
    @DisplayName("MEMBER의 수정 요청은 MR-004로 거절한다")
    void rejectsNonManagementRole() {
        /* 웹 @PreAuthorize를 우회해 서비스를 직접 호출하는 상황을 준비한다. */
        MeetingRoomUpdateService service = service(new RecordingCommandRepository(existingRoom()));

        /* MEMBER가 위치를 변경하려는 요청은 관리 권한 오류여야 한다. */
        UpdateMeetingRoomCommand command = command(
                "MEMBER",
                false, null,
                true, "본관 3층"
        );
        assertErrorCode(() -> service.updateMeetingRoom(command), "MR-004");
    }

    /* 회사 범위의 활성 회의실이 없을 때 존재 여부를 숨기는지 검증한다. */
    @Test
    @DisplayName("타 회사·비활성·미존재 회의실은 MR-001로 거절한다")
    void rejectsMissingActiveMeetingRoom() {
        /* 잠금 조회 결과가 없는 명령 저장소로 수정 서비스를 준비한다. */
        MeetingRoomUpdateService service = service(new RecordingCommandRepository(null));

        /* 정상 형식의 수정 요청이어도 회사 범위에서 회의실을 찾지 못하면 404 계약이어야 한다. */
        assertErrorCode(() -> service.updateMeetingRoom(locationCommand("OWNER", "본관 3층")), "MR-001");
    }

    /* 자기 자신을 제외한 활성 이름 중복이 MR-002인지 검증한다. */
    @Test
    @DisplayName("변경한 이름이 다른 활성 회의실과 중복되면 MR-002로 거절한다")
    void rejectsDuplicateName() {
        /* 다른 활성 회의실이 동일 이름을 사용한다고 응답하는 저장소를 준비한다. */
        RecordingCommandRepository commandRepository = new RecordingCommandRepository(existingRoom());
        commandRepository.duplicateExcludingCurrent = true;
        MeetingRoomUpdateService service = service(commandRepository);

        /* 새로운 이름으로 변경하려는 요청은 현재 ID를 제외한 중복 조회 뒤 거절돼야 한다. */
        UpdateMeetingRoomCommand command = command(
                "OWNER",
                true, " 대회의실 ",
                false, null
        );
        assertErrorCode(() -> service.updateMeetingRoom(command), "MR-002");
        assertThat(commandRepository.checkedExcludedMeetingRoomId).isEqualTo(2L);
        assertThat(commandRepository.checkedName).isEqualTo("대회의실");
    }

    /* 테스트 대상 서비스를 명령 저장소 대역으로 조립한다. */
    private MeetingRoomUpdateService service(MeetingRoomCommandRepository commandRepository) {
        /* 운영과 같은 저장 경계를 사용하는 서비스를 반환한다. */
        return new MeetingRoomUpdateService(commandRepository);
    }

    /* 위치 하나만 바꾸는 정상 형식 명령을 만든다. */
    private UpdateMeetingRoomCommand locationCommand(String role, String location) {
        /* 공통 식별자와 전달된 역할·위치를 PATCH 명령으로 구성한다. */
        return command(
                role,
                false, null,
                true, location
        );
    }

    /* 테스트별 PATCH 필드 존재 여부와 값을 하나의 명령으로 만든다. */
    private UpdateMeetingRoomCommand command(
            String role,
            boolean nameProvided,
            String name,
            boolean locationProvided,
            String location
    ) {
        /* 회사 10의 2번 회의실을 대상으로 전달받은 부분 수정값을 사용한다. */
        return new UpdateMeetingRoomCommand(
                10L,
                role,
                2L,
                nameProvided,
                name,
                locationProvided,
                location
        );
    }

    /* 테스트에서 수정할 기존 활성 회의실을 만든다. */
    private MeetingRoom existingRoom() {
        /* 명세 예시와 같은 회사·위치 속성을 가진 2번 회의실이다. */
        return new MeetingRoom(
                2L,
                10L,
                "회의실 B",
                "박애관 422호",
                null
        );
    }

    /* 실행 결과가 특정 비즈니스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* 예외 타입과 공개 오류 코드를 함께 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 수정 서비스가 실행한 잠금·중복·저장 요청을 기록하는 명령 저장소 대역이다. */
    private static final class RecordingCommandRepository implements MeetingRoomCommandRepository {

        /* 잠금 조회에서 반환할 활성 회의실이다. */
        private final MeetingRoom currentMeetingRoom;

        /* 현재 회의실을 제외한 이름 중복 조회 결과다. */
        private boolean duplicateExcludingCurrent;

        /* 이름 중복 조회에 전달된 정규화 이름이다. */
        private String checkedName;

        /* 이름 중복 조회에서 제외한 현재 회의실 식별자다. */
        private Long checkedExcludedMeetingRoomId;

        /* 저장 요청에 전달된 최종 회의실 상태다. */
        private MeetingRoom savedMeetingRoom;

        /* 잠금 조회에서 반환할 회의실로 저장소 대역을 생성한다. */
        private RecordingCommandRepository(MeetingRoom currentMeetingRoom) {
            /* null이면 회사 범위의 활성 회의실이 없는 상황을 뜻한다. */
            this.currentMeetingRoom = currentMeetingRoom;
        }

        /* ROOM-03 전체 이름 중복 조회는 ROOM-04 테스트에서 사용하지 않는다. */
        @Override
        public boolean existsActiveByCompanyIdAndName(Long companyId, String name) {
            /* 호출되지 않는 등록 계약이므로 중복이 없다고 반환한다. */
            return false;
        }

        /* 수정 대상 활성 회의실의 잠금 조회 결과를 반환한다. */
        @Override
        public Optional<MeetingRoom> findActiveByIdForUpdate(Long companyId, Long meetingRoomId) {
            /* 준비된 회의실이 없으면 빈 결과를 반환한다. */
            return Optional.ofNullable(currentMeetingRoom);
        }

        /* 자기 자신 제외 이름 중복 조건을 기록하고 준비된 결과를 반환한다. */
        @Override
        public boolean existsActiveByCompanyIdAndNameExcludingId(
                Long companyId,
                String name,
                Long excludedMeetingRoomId
        ) {
            /* 서비스가 넘긴 정규화 이름과 제외 ID를 검증할 수 있도록 기록한다. */
            this.checkedName = name;
            this.checkedExcludedMeetingRoomId = excludedMeetingRoomId;
            return duplicateExcludingCurrent;
        }

        /* 최종 회의실 상태를 기록하고 그대로 저장 결과로 반환한다. */
        @Override
        public MeetingRoom save(MeetingRoom meetingRoom) {
            /* 실제 DB merge 대신 메모리에 변경 상태를 보관한다. */
            this.savedMeetingRoom = meetingRoom;
            return meetingRoom;
        }
    }

    /* 미래 예약 조회 조건과 호출 횟수를 기록하는 저장소 대역이다. */
    private static final class RecordingReservationRepository implements MeetingRoomReservationRepository {

        /* 조회 시 반환할 미래 SCHEDULED 예약 목록이다. */
        private final List<ScheduledMeetingReservation> reservations;

        /* 예약 조회가 실행된 횟수다. */
        private int calls;

        /* 예약 조회에 전달된 회사 식별자다. */
        private Long capturedCompanyId;

        /* 예약 조회에 전달된 회의실 식별자다. */
        private Long capturedMeetingRoomId;

        /* 예약 조회에 전달된 현재 시각이다. */
        private LocalDateTime capturedFrom;

        /* 테스트별 반환 예약 목록으로 저장소 대역을 생성한다. */
        private RecordingReservationRepository(List<ScheduledMeetingReservation> reservations) {
            /* 외부에서 목록을 바꾸지 못하도록 복사해 보관한다. */
            this.reservations = new ArrayList<>(reservations);
        }

        /* 조회 조건을 기록하고 준비된 미래 예약 목록을 반환한다. */
        @Override
        public List<ScheduledMeetingReservation> findFutureScheduledReservations(
                Long companyId,
                Long meetingRoomId,
                LocalDateTime fromInclusive
        ) {
            /* 호출 횟수와 테넌트·회의실·시간 조건을 검증할 수 있도록 기록한다. */
            calls++;
            capturedCompanyId = companyId;
            capturedMeetingRoomId = meetingRoomId;
            capturedFrom = fromInclusive;
            return List.copyOf(reservations);
        }
    }
}
