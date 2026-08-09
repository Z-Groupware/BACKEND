package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.command.CreateMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCreationResult;
import com.module06.backend.meeting.application.usecase.CreateMeetingUseCase;
import com.module06.backend.meeting.application.usecase.EnterMeetingUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.request.CreateMeetingRequest;
import com.module06.backend.meeting.presentation.api.response.CreateMeetingResponse;

/*
 * MEET-01 Controller가 인증 정보와 본문을 명령으로 결합하고 응답 계약을 지키는지 검증한다.
 */
@DisplayName("MEET-01 회의 예약 Controller")
class MeetingControllerTest {

    /* MEET-01 테스트에서 호출되면 실패하는 MEET-07 입장 유스케이스 대역이다. */
    private static final EnterMeetingUseCase UNUSED_ENTRY_USE_CASE = command -> {
        throw new AssertionError("MEET-01 예약에서는 입장 유스케이스를 호출하면 안 됩니다.");
    };

    /* 인증 principal 값과 요청 본문이 유스케이스에 전달되고 응답으로 변환되는지 확인한다. */
    @Test
    @DisplayName("회의 예약 결과를 201 공통 응답으로 반환한다")
    void returnsCreatedMeetingResponse() {
        /* 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        CreateMeetingCommand[] capturedCommand = new CreateMeetingCommand[1];

        /* 명령을 기록하고 명세 예시 결과를 반환하는 유스케이스 대역을 만든다. */
        CreateMeetingUseCase useCase = command -> {
            capturedCommand[0] = command;
            return result();
        };
        MeetingController controller = new MeetingController(useCase, UNUSED_ENTRY_USE_CASE);

        /* 녹음 동의 값을 생략하고 개설자가 없는 참석자 요청을 준비한다. */
        CreateMeetingRequest request = new CreateMeetingRequest(
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                null,
                305L,
                List.of(7L, 11L)
        );

        /* 인증 principal에서 추출됐다고 가정한 값과 요청 본문으로 API 메서드를 호출한다. */
        ApiResponse<CreateMeetingResponse> response = controller.createMeeting(10L, 3L, 100L, request);

        /* 실제 HTTP 상태 어노테이션과 같은 201 값과 성공 메시지가 래퍼에도 담겨야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("회의를 예약했습니다.");

        /* 인증 정보는 본문과 무관하게 유스케이스 명령에 전달돼야 한다. */
        assertThat(capturedCommand[0].companyId()).isEqualTo(10L);
        assertThat(capturedCommand[0].hostMemberId()).isEqualTo(3L);
        assertThat(capturedCommand[0].hostTeamId()).isEqualTo(100L);

        /* 생략한 녹음 동의 값은 명세 기본값 false로 전달돼야 한다. */
        assertThat(capturedCommand[0].recordingConsent()).isFalse();

        /* API 일시는 초를 생략하지 않는 고정 형식으로 직렬화 준비돼야 한다. */
        assertThat(response.getData().startAt()).isEqualTo("2026-08-06T14:00:00");
        assertThat(response.getData().endAt()).isEqualTo("2026-08-06T15:00:00");

        /* 회의실과 개설자, 참석자 중첩 구조가 명세와 일치해야 한다. */
        assertThat(response.getData().meetingRoom().meetingRoomId()).isEqualTo(2L);
        assertThat(response.getData().host().memberId()).isEqualTo(3L);
        assertThat(response.getData().attendees())
                .extracting(CreateMeetingResponse.AttendeeResponse::memberId)
                .containsExactly(3L, 7L, 11L);
    }

    /* Controller 대역이 반환할 완성된 애플리케이션 결과를 만든다. */
    private MeetingCreationResult result() {
        /* 명세 예시와 동일한 회의실, 개설자, 참석자 값을 사용한다. */
        return new MeetingCreationResult(
                91L,
                MeetingStatus.SCHEDULED,
                "A커머스 온보딩 킥오프",
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                false,
                new MeetingCreationResult.MeetingRoom(2L, "회의실 B", "박애관 422호"),
                new MeetingCreationResult.Host(3L, "지우"),
                List.of(
                        new MeetingCreationResult.Attendee(3L, "지우", "기획"),
                        new MeetingCreationResult.Attendee(7L, "이든", "개발"),
                        new MeetingCreationResult.Attendee(11L, "하린", "디자인")
                )
        );
    }
}
