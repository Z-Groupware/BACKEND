package com.module06.backend.meetingroom.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.application.command.CreateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;

/*
 * ROOM-03 회의실 등록의 정규화·중복·이용 시간 검증을 확인하는 애플리케이션 단위 테스트다.
 */
@DisplayName("ROOM-03 회의실 등록 서비스")
class MeetingRoomCommandServiceTest {

    /* 정상 요청이 회사 범위를 유지하고 정규화된 회의실로 저장되는지 검증한다. */
    @Test
    @DisplayName("검증된 회의실을 등록하고 생성 식별자를 반환한다")
    void createsMeetingRoomAndReturnsGeneratedId() {
        /* 저장 요청을 기록하고 데이터베이스 생성 식별자를 흉내 내는 저장소 대역을 준비한다. */
        RecordingMeetingRoomCommandRepository repository = new RecordingMeetingRoomCommandRepository(false);
        MeetingRoomCommandService service = new MeetingRoomCommandService(repository);

        /* 가장자리 공백과 빈 위치가 포함된 정상 등록 명령을 실행한다. */
        MeetingRoomCreationResult result = service.createMeetingRoom(new CreateMeetingRoomCommand(
                10L,
                "  대회의실  ",
                "   ",
                12,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        ));

        /* 생성 식별자와 회사 범위가 저장 결과에 반영돼야 한다. */
        assertThat(result.meetingRoomId()).isEqualTo(101L);
        assertThat(repository.savedMeetingRoom.getCompanyId()).isEqualTo(10L);

        /* 이름은 공백이 제거되고 빈 위치는 null로 정규화돼야 한다. */
        assertThat(repository.savedMeetingRoom.getName()).isEqualTo("대회의실");
        assertThat(repository.savedMeetingRoom.getLocation()).isNull();
        assertThat(repository.savedMeetingRoom.isActive()).isTrue();
    }

    /* 같은 회사의 활성 회의실 이름이 중복되면 저장하지 않는지 검증한다. */
    @Test
    @DisplayName("활성 회의실 이름이 중복되면 MR-002로 거절한다")
    void rejectsDuplicateActiveMeetingRoomName() {
        /* 중복 이름이 존재한다고 응답하는 저장소 대역을 준비한다. */
        RecordingMeetingRoomCommandRepository repository = new RecordingMeetingRoomCommandRepository(true);
        MeetingRoomCommandService service = new MeetingRoomCommandService(repository);

        /* 가장자리 공백을 제거한 이름으로도 중복이 확인되는지 실행한다. */
        assertThatThrownBy(() -> service.createMeetingRoom(validCommand(" 대회의실 ")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MR-002");

        /* 중복 조회에는 정규화된 이름이 전달되고 저장은 실행되지 않아야 한다. */
        assertThat(repository.checkedCompanyId).isEqualTo(10L);
        assertThat(repository.checkedName).isEqualTo("대회의실");
        assertThat(repository.savedMeetingRoom).isNull();
    }

    /* 종료 시각이 시작 시각보다 늦지 않은 요청을 도메인 오류로 구분하는지 검증한다. */
    @Test
    @DisplayName("종료 시각이 시작 시각보다 늦지 않으면 MR-003으로 거절한다")
    void rejectsInvalidAvailableTimeRange() {
        /* 데이터베이스를 호출하지 않는 빈 저장소와 등록 서비스를 준비한다. */
        RecordingMeetingRoomCommandRepository repository = new RecordingMeetingRoomCommandRepository(false);
        MeetingRoomCommandService service = new MeetingRoomCommandService(repository);

        /* 시작과 종료가 같은 명령은 이용 가능 시간 범위 오류여야 한다. */
        CreateMeetingRoomCommand command = new CreateMeetingRoomCommand(
                10L,
                "대회의실",
                "박애관 421호",
                12,
                LocalTime.of(9, 0),
                LocalTime.of(9, 0)
        );
        assertThatThrownBy(() -> service.createMeetingRoom(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MR-003");

        /* 입력 검증 실패 뒤에는 중복 조회와 저장이 실행되지 않아야 한다. */
        assertThat(repository.checkedName).isNull();
        assertThat(repository.savedMeetingRoom).isNull();
    }

    /* 30분 슬롯 경계가 아닌 시각을 공통 입력 오류로 거절하는지 검증한다. */
    @Test
    @DisplayName("이용 가능 시각이 30분 경계가 아니면 Z-001로 거절한다")
    void rejectsTimeOutsideSlotGrid() {
        /* 데이터베이스를 호출하지 않는 빈 저장소와 등록 서비스를 준비한다. */
        RecordingMeetingRoomCommandRepository repository = new RecordingMeetingRoomCommandRepository(false);
        MeetingRoomCommandService service = new MeetingRoomCommandService(repository);

        /* 09시 10분은 공용 30분 슬롯 그리드에 포함되지 않는다. */
        CreateMeetingRoomCommand command = new CreateMeetingRoomCommand(
                10L,
                "대회의실",
                "박애관 421호",
                12,
                LocalTime.of(9, 10),
                LocalTime.of(18, 0)
        );
        assertThatThrownBy(() -> service.createMeetingRoom(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }

    /* 필수값과 양수 조건을 서비스 경계에서도 방어하는지 검증한다. */
    @Test
    @DisplayName("회사·이름·수용 인원 필수 조건을 위반하면 Z-001로 거절한다")
    void rejectsInvalidRequiredValues() {
        /* 웹 계층을 우회하는 호출을 검증할 등록 서비스를 준비한다. */
        RecordingMeetingRoomCommandRepository repository = new RecordingMeetingRoomCommandRepository(false);
        MeetingRoomCommandService service = new MeetingRoomCommandService(repository);

        /* 유효하지 않은 회사 식별자와 빈 이름, 0명을 가진 명령을 실행한다. */
        CreateMeetingRoomCommand command = new CreateMeetingRoomCommand(
                0L,
                " ",
                null,
                0,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
        assertThatThrownBy(() -> service.createMeetingRoom(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }

    /* 정상 테스트에서 반복해서 사용하는 ROOM-03 명령을 생성한다. */
    private CreateMeetingRoomCommand validCommand(String name) {
        /* 이름 이외의 필드는 모두 정상적인 회의실 등록값으로 채운다. */
        return new CreateMeetingRoomCommand(
                10L,
                name,
                "박애관 421호",
                12,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
    }

    /* 서비스가 실행한 중복 조회와 저장을 기록하는 명령 저장소 대역이다. */
    private static final class RecordingMeetingRoomCommandRepository implements MeetingRoomCommandRepository {

        /* 중복 조회에서 반환할 활성 이름 존재 여부다. */
        private final boolean duplicate;

        /* 중복 조회에 전달된 회사 식별자다. */
        private Long checkedCompanyId;

        /* 중복 조회에 전달된 정규화 이름이다. */
        private String checkedName;

        /* 저장 메서드에 전달된 신규 회의실이다. */
        private MeetingRoom savedMeetingRoom;

        /* 테스트별 중복 조건으로 저장소 대역을 생성한다. */
        private RecordingMeetingRoomCommandRepository(boolean duplicate) {
            /* 전달받은 조건을 중복 조회 결과로 보관한다. */
            this.duplicate = duplicate;
        }

        /* 중복 조회 조건을 기록하고 준비된 결과를 반환한다. */
        @Override
        public boolean existsActiveByCompanyIdAndName(Long companyId, String name) {
            /* 서비스가 정규화한 회사와 이름을 검증할 수 있도록 기록한다. */
            this.checkedCompanyId = companyId;
            this.checkedName = name;
            return duplicate;
        }

        /* 저장 요청을 기록하고 생성 식별자가 반영된 도메인 객체를 반환한다. */
        @Override
        public MeetingRoom save(MeetingRoom meetingRoom) {
            /* 신규 도메인을 보관한 뒤 데이터베이스 IDENTITY 결과를 흉내 낸다. */
            this.savedMeetingRoom = meetingRoom;
            return new MeetingRoom(
                    101L,
                    meetingRoom.getCompanyId(),
                    meetingRoom.getName(),
                    meetingRoom.getLocation(),
                    meetingRoom.getCapacity(),
                    meetingRoom.getAvailableFrom(),
                    meetingRoom.getAvailableTo(),
                    meetingRoom.getDeletedAt()
            );
        }
    }
}
