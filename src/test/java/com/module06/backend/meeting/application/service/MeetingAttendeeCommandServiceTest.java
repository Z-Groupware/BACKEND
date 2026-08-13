package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.command.ReplaceMeetingAttendeesCommand;
import com.module06.backend.meeting.application.event.MeetingAttendeesAddedEvent;
import com.module06.backend.meeting.application.event.MeetingAttendeesRemovedEvent;
import com.module06.backend.meeting.application.event.MeetingReservedEvent;
import com.module06.backend.meeting.application.port.out.MeetingEventPublisher;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingLockRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingRepository;

/*
 * MEET-09 서비스의 권한·상태·구성원 검증과 차이 이벤트 발행 규칙을 검증한다.
 */
@DisplayName("MEET-09 참석자 명단 교체 서비스")
class MeetingAttendeeCommandServiceTest {

    /* host를 자동 포함하고 새 참석자만 이벤트로 발행하는 정상 교체를 검증한다. */
    @Test
    @DisplayName("host를 자동 포함하고 새 참석자에게만 초대 이벤트를 발행한다")
    void replacesRosterAndPublishesOnlyAddedAttendees() {
        /* 기존 3·7 명단과 SCHEDULED 회의를 반환하는 저장소 대역을 준비한다. */
        RecordingMeetingRepository writeRepository = new RecordingMeetingRepository();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.SCHEDULED, List.of(3L, 7L)),
                writeRepository,
                eventPublisher,
                members(3L, 7L, 11L)
        );

        /* host 3번을 생략하고 기존 7번과 신규 11번을 중복 포함한 전체 교체를 요청한다. */
        MeetingAttendeeUpdateResult result = service.replaceMeetingAttendees(command(
                3L,
                "MEMBER",
                false,
                List.of(7L, 11L, 7L)
        ));

        /* 저장 명단과 응답은 host 우선이며 중복이 제거된 3·7·11 순서여야 한다. */
        assertThat(writeRepository.replacedMeetingId).isEqualTo(91L);
        assertThat(writeRepository.replacedMemberIds).containsExactly(3L, 7L, 11L);
        assertThat(result.attendees())
                .extracting(MeetingAttendeeUpdateResult.Attendee::memberId)
                .containsExactly(3L, 7L, 11L);

        /* 기존 명단에 없던 11번 구성원만 초대 이벤트 대상이어야 한다. */
        assertThat(eventPublisher.attendeeEvents).singleElement().satisfies(event ->
                assertThat(event.addedAttendeeMemberIds()).containsExactly(11L)
        );

        /* 제외된 구성원이 없으므로 제외 이벤트는 발행되지 않아야 한다. */
        assertThat(eventPublisher.removedEvents).isEmpty();
    }

    /* 기존 명단에만 있던 구성원이 빠지면 제외 이벤트만 발행되는지 검증한다. */
    @Test
    @DisplayName("명단에서 빠진 구성원만 제외 이벤트를 발행한다")
    void publishesRemovedEventForDroppedAttendee() {
        /* 기존 3·7·11 명단에서 11번을 뺀 교체를 요청한다. */
        RecordingMeetingRepository writeRepository = new RecordingMeetingRepository();
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.SCHEDULED, List.of(3L, 7L, 11L)),
                writeRepository,
                eventPublisher,
                members(3L, 7L)
        );

        service.replaceMeetingAttendees(command(3L, "MEMBER", false, List.of(7L)));

        /* 새로 추가된 구성원이 없으므로 추가 이벤트는 발행되지 않아야 한다. */
        assertThat(eventPublisher.attendeeEvents).isEmpty();

        /* 기존 명단에만 있던 11번만 제외 이벤트 대상이어야 한다. */
        assertThat(eventPublisher.removedEvents).singleElement().satisfies(event ->
                assertThat(event.removedAttendeeMemberIds()).containsExactly(11L)
        );
    }

    /* 진행 중 회의에서도 OWNER가 참석자 명단을 교체할 수 있는지 검증한다. */
    @Test
    @DisplayName("OWNER는 진행 중 회의의 참석자 명단을 교체할 수 있다")
    void ownerReplacesRosterDuringMeeting() {
        /* 진행 중 회의와 정상 구성원을 반환하는 서비스를 준비한다. */
        RecordingMeetingRepository writeRepository = new RecordingMeetingRepository();
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.IN_PROGRESS, List.of(3L, 7L)),
                writeRepository,
                new RecordingEventPublisher(),
                members(3L, 11L)
        );

        /* 회의 개설자가 아닌 99번 OWNER가 11번 참석자로 전체 교체한다. */
        service.replaceMeetingAttendees(command(99L, "OWNER", false, List.of(11L)));

        /* SCHEDULED와 동일하게 host 자동 포함 최종 명단이 저장돼야 한다. */
        assertThat(writeRepository.replacedMemberIds).containsExactly(3L, 11L);
    }

    /* 일반 비개설자가 다른 회의의 명단을 바꾸지 못하는지 검증한다. */
    @Test
    @DisplayName("일반 비개설자는 MT-006으로 거절한다")
    void rejectsNonHostMember() {
        /* 정상 예약 회의지만 요청자가 host가 아닌 서비스를 준비한다. */
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.SCHEDULED, List.of(3L, 7L)),
                new RecordingMeetingRepository(),
                new RecordingEventPublisher(),
                members(3L, 7L)
        );

        /* 7번 MEMBER의 변경 요청은 구성원 조회와 저장 전에 거절돼야 한다. */
        assertErrorCode(
                () -> service.replaceMeetingAttendees(command(7L, "MEMBER", false, List.of(7L))),
                "MT-006"
        );
    }

    /* 종료된 회의의 확정 참석자 명단을 변경하지 못하는지 검증한다. */
    @Test
    @DisplayName("종료된 회의는 MT-009로 거절한다")
    void rejectsDoneMeeting() {
        /* host가 요청하지만 회의 상태가 DONE인 서비스를 준비한다. */
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.DONE, List.of(3L, 7L)),
                new RecordingMeetingRepository(),
                new RecordingEventPublisher(),
                members(3L, 7L)
        );

        /* 종료 상태는 권한 검증을 통과해도 명단 교체가 불가능해야 한다. */
        assertErrorCode(
                () -> service.replaceMeetingAttendees(command(3L, "MEMBER", false, List.of(7L))),
                "MT-009"
        );
    }

    /* 타 회사 또는 삭제 구성원이 포함된 불완전한 배치 결과를 거절하는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 구성원이 포함되면 MT-010으로 거절한다")
    void rejectsInvalidMember() {
        /* 요청한 11번을 반환하지 않는 B 구성원 Port로 서비스를 준비한다. */
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.SCHEDULED, List.of(3L, 7L)),
                new RecordingMeetingRepository(),
                new RecordingEventPublisher(),
                members(3L, 7L)
        );

        /* 최종 명단 일부가 누락되면 저장 전에 전체 요청이 MT-010으로 실패해야 한다. */
        assertErrorCode(
                () -> service.replaceMeetingAttendees(command(3L, "MEMBER", false, List.of(7L, 11L))),
                "MT-010"
        );
    }

    /* null·0·음수 참석자 식별자가 정규화나 Port 호출 전에 공통 입력 오류로 거절되는지 검증한다. */
    @Test
    @DisplayName("형식이 잘못된 참석자 식별자는 Z-001로 거절한다")
    void rejectsMalformedMemberIdsBeforeNormalization() {
        /* 정상 회의를 반환하지만 잘못된 요청은 조회 전에 차단할 서비스를 준비한다. */
        MeetingAttendeeCommandService service = service(
                meeting(MeetingStatus.SCHEDULED, List.of(3L, 7L)),
                new RecordingMeetingRepository(),
                new RecordingEventPublisher(),
                members(3L, 7L)
        );

        /* null 요소가 포함돼도 List 복사 단계의 NPE가 아니라 명시적인 Z-001이어야 한다. */
        assertErrorCode(
                () -> service.replaceMeetingAttendees(command(
                        3L,
                        "MEMBER",
                        false,
                        java.util.Arrays.asList(7L, null)
                )),
                "Z-001"
        );

        /* 0과 음수 식별자도 구성원 Port에 전달하지 않고 동일한 입력 오류로 거절해야 한다. */
        assertErrorCode(
                () -> service.replaceMeetingAttendees(command(3L, "MEMBER", false, List.of(0L, -1L))),
                "Z-001"
        );
    }

    /* 테스트 입력으로 실제 MEET-09 서비스를 조립한다. */
    private MeetingAttendeeCommandService service(
            MeetingQueryRepository.MeetingSnapshot meeting,
            RecordingMeetingRepository writeRepository,
            RecordingEventPublisher eventPublisher,
            List<MemberQueryPort.MemberSnapshot> members
    ) {
        /* 회의 행 잠금 이후 최신 참석자 명단을 반환하는 명령 전용 저장소 대역을 만든다. */
        MeetingLockRepository lockRepository = (companyId, meetingId) -> Optional.of(meeting);

        /* 요청에 따라 구성원 표시 정보를 반환하는 B Port 대역을 만든다. */
        MemberQueryPort memberQueryPort = (companyId, memberIds) -> {
            boolean invalid = memberIds.stream()
                    .anyMatch(memberId -> memberId == null || memberId <= 0L);

            if (invalid) {
                throw new AssertionError("잘못된 구성원 ID가 MemberQueryPort까지 전달됐습니다.");
            }

            return members;
        };

        /* 실제 서비스에 조회·쓰기·구성원·이벤트 대역을 주입해 반환한다. */
        return new MeetingAttendeeCommandService(
                lockRepository,
                writeRepository,
                memberQueryPort,
                eventPublisher
        );
    }

    /* 요청자 정보와 교체 명단으로 정상 MEET-09 Command를 만든다. */
    private ReplaceMeetingAttendeesCommand command(
            Long requesterMemberId,
            String requesterRole,
            boolean requesterAdmin,
            List<Long> attendeeMemberIds
    ) {
        /* 회사와 회의 식별자는 정상값으로 고정하고 테스트별 권한과 명단만 바꾼다. */
        return new ReplaceMeetingAttendeesCommand(
                10L,
                requesterMemberId,
                requesterRole,
                requesterAdmin,
                91L,
                attendeeMemberIds
        );
    }

    /* 상태와 기존 명단으로 MEET-09 단건 회의 조회 모델을 만든다. */
    private MeetingQueryRepository.MeetingSnapshot meeting(
            MeetingStatus status,
            List<Long> attendeeMemberIds
    ) {
        /* 권한·상태·이벤트 생성에 필요한 실제 회의 필드를 모두 채운다. */
        return new MeetingQueryRepository.MeetingSnapshot(
                91L,
                10L,
                12L,
                3L,
                "A커머스 온보딩 킥오프",
                status,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                null,
                null,
                attendeeMemberIds
        );
    }

    /* 전달받은 구성원 식별자에 대응하는 활성 구성원 표시 정보를 만든다. */
    private List<MemberQueryPort.MemberSnapshot> members(Long... memberIds) {
        /* 테스트 식별자별 이름과 팀을 안정적인 값으로 변환한다. */
        return java.util.Arrays.stream(memberIds)
                .map(memberId -> new MemberQueryPort.MemberSnapshot(
                        memberId,
                        "구성원" + memberId,
                        memberId * 10,
                        "팀" + memberId
                ))
                .toList();
    }

    /* 실행 결과가 예상 서비스 오류 코드인지 검증한다. */
    private void assertErrorCode(Runnable execution, String expectedCode) {
        /* BusinessException 타입과 외부 오류 코드를 함께 확인한다. */
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }

    /* 저장된 최종 참석자 식별자 목록을 기록하는 회의 쓰기 저장소 대역이다. */
    private static final class RecordingMeetingRepository implements MeetingRepository {

        /* 교체 요청을 받은 대상 회의 식별자다. */
        private Long replacedMeetingId;

        /* 교체 요청을 받은 개설자 포함 최종 참석자 목록이다. */
        private List<Long> replacedMemberIds = List.of();

        /* 신규 예약 저장은 MEET-09 서비스 테스트에서 사용하지 않는다. */
        @Override
        public Meeting saveReservation(Meeting meeting) {
            /* 호출되지 않는 계약이므로 테스트 실패로 잘못된 경로를 드러낸다. */
            throw new AssertionError("MEET-09에서는 신규 회의를 저장하면 안 됩니다.");
        }

        /* 대상 회의와 최종 참석자 명단을 검증할 수 있도록 기록한다. */
        @Override
        public void replaceAttendees(Long meetingId, List<Long> attendeeMemberIds) {
            /* 외부 변경이 테스트 기록에 영향을 주지 않도록 목록을 복사한다. */
            this.replacedMeetingId = meetingId;
            this.replacedMemberIds = List.copyOf(attendeeMemberIds);
        }
    }

    /* MEET-09가 발행한 참석자 추가·제외 이벤트를 기록하는 이벤트 Publisher 대역이다. */
    private static final class RecordingEventPublisher implements MeetingEventPublisher {

        /* 검증할 참석자 추가 이벤트 목록이다. */
        private final List<MeetingAttendeesAddedEvent> attendeeEvents = new ArrayList<>();

        /* 검증할 참석자 제외 이벤트 목록이다. */
        private final List<MeetingAttendeesRemovedEvent> removedEvents = new ArrayList<>();

        /* 예약 이벤트는 MEET-09 서비스 테스트에서 사용하지 않는다. */
        @Override
        public void publish(MeetingReservedEvent event) {
            /* 호출되지 않는 이벤트 경로를 테스트 실패로 드러낸다. */
            throw new AssertionError("MEET-09에서는 예약 이벤트를 발행하면 안 됩니다.");
        }

        /* 새 참석자 추가 이벤트를 검증 목록에 기록한다. */
        @Override
        public void publish(MeetingAttendeesAddedEvent event) {
            /* 이벤트 발행 횟수와 대상 구성원을 확인할 수 있도록 저장한다. */
            attendeeEvents.add(event);
        }

        /* 참석자 제외 이벤트를 검증 목록에 기록한다. */
        @Override
        public void publish(MeetingAttendeesRemovedEvent event) {
            /* 이벤트 발행 횟수와 대상 구성원을 확인할 수 있도록 저장한다. */
            removedEvents.add(event);
        }
    }
}
