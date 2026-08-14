package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.command.CreateMeetingCommand;
import com.module06.backend.meeting.application.command.CreateOnlineMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCreationResult;
import com.module06.backend.meeting.application.result.OnlineMeetingCreationResult;
import com.module06.backend.meeting.application.usecase.CreateMeetingUseCase;
import com.module06.backend.meeting.application.usecase.CreateOnlineMeetingUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.request.CreateMeetingRequest;
import com.module06.backend.meeting.presentation.api.request.CreateOnlineMeetingRequest;
import com.module06.backend.meeting.presentation.api.response.CreateMeetingResponse;
import com.module06.backend.meeting.presentation.api.response.CreateOnlineMeetingResponse;

/*
 * MEET-01·MEET-18 Controller가 인증 정보와 본문을 명령으로 결합하고 응답 계약을 지키는지 검증한다.
 */
@DisplayName("MEET-01·MEET-18 회의 개설 Controller")
class MeetingControllerTest {

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
        CreateOnlineMeetingUseCase onlineUseCase = command -> {
            throw new AssertionError("MEET-01 테스트에서는 온라인 회의 개설을 호출하면 안 됩니다.");
        };
        MeetingController controller = new MeetingController(useCase, onlineUseCase);

        /* 녹음 동의 값을 생략하고 개설자가 없는 참석자 요청을 준비한다. */
        CreateMeetingRequest request = new CreateMeetingRequest(
                "A커머스 온보딩 킥오프",
                12L,
                2L,
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                null,
                305L,
                List.of(7L, 11L),
                "스프린트 진행 상황",
                List.of("개발 진행률 점검")
        );

        /* 인증 principal에서 추출됐다고 가정한 값과 요청 본문으로 API 메서드를 호출한다. */
        ApiResponse<CreateMeetingResponse> response = controller.createMeeting(10L, 3L, 100L, "LEADER", request);

        /* 실제 HTTP 상태 어노테이션과 같은 201 값과 성공 메시지가 래퍼에도 담겨야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("회의를 예약했습니다.");

        /* 인증 정보는 본문과 무관하게 유스케이스 명령에 전달돼야 한다. */
        assertThat(capturedCommand[0].companyId()).isEqualTo(10L);
        assertThat(capturedCommand[0].hostMemberId()).isEqualTo(3L);
        assertThat(capturedCommand[0].hostTeamId()).isEqualTo(100L);
        assertThat(capturedCommand[0].hostRole()).isEqualTo("LEADER");

        /* 회의 개설 화면의 대주제와 소주제가 명령에 그대로 전달돼야 한다. */
        assertThat(capturedCommand[0].mainTopic()).isEqualTo("스프린트 진행 상황");
        assertThat(capturedCommand[0].subTopics()).containsExactly("개발 진행률 점검");

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

    /* 인증 principal 값과 요청 본문이 온라인 회의 개설 명령으로 결합되고 응답으로 변환되는지 확인한다. */
    @Test
    @DisplayName("비대면 회의 개설 결과를 201 공통 응답으로 반환한다")
    void returnsCreatedOnlineMeetingResponse() {
        /* 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        CreateOnlineMeetingCommand[] capturedCommand = new CreateOnlineMeetingCommand[1];

        /* 명령을 기록하고 온라인 회의 명세 예시 결과를 반환하는 유스케이스 대역을 만든다. */
        CreateOnlineMeetingUseCase onlineUseCase = command -> {
            capturedCommand[0] = command;
            return onlineResult();
        };
        CreateMeetingUseCase useCase = command -> {
            throw new AssertionError("MEET-18 테스트에서는 대면 회의 개설을 호출하면 안 됩니다.");
        };
        MeetingController controller = new MeetingController(useCase, onlineUseCase);

        /* 회의실·시작·종료 일시·녹음 동의 필드 자체가 없는 비대면 회의 개설 요청을 준비한다. */
        CreateOnlineMeetingRequest request = new CreateOnlineMeetingRequest(
                "비대면 스프린트 회고",
                12L,
                305L,
                List.of(7L, 11L),
                "스프린트 회고",
                List.of("개선 항목 정리"),
                new CreateOnlineMeetingRequest.RecordingRequest(
                        "recordings/org-10/member-3/online-pending/upload-id/meeting.mp3",
                        "meeting.mp3",
                        "audio/mpeg",
                        1_024L
                )
        );

        /* 인증 principal에서 추출됐다고 가정한 값과 요청 본문으로 API 메서드를 호출한다. */
        ApiResponse<CreateOnlineMeetingResponse> response =
                controller.createOnlineMeeting(10L, 3L, 100L, "LEADER", request);

        /* 실제 HTTP 상태 어노테이션과 같은 201 값과 성공 메시지가 래퍼에도 담겨야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("비대면 회의를 개설했습니다.");

        /* 인증 정보는 본문과 무관하게 유스케이스 명령에 전달돼야 한다. */
        assertThat(capturedCommand[0].companyId()).isEqualTo(10L);
        assertThat(capturedCommand[0].hostMemberId()).isEqualTo(3L);
        assertThat(capturedCommand[0].hostTeamId()).isEqualTo(100L);
        assertThat(capturedCommand[0].hostRole()).isEqualTo("LEADER");

        /* 비대면 회의는 녹음 동의 없이 성립하지 않으므로 요청 필드 없이 항상 true로 고정돼야 한다. */
        assertThat(capturedCommand[0].recordingConsent()).isTrue();
        assertThat(capturedCommand[0].recording().fileName()).isEqualTo("meeting.mp3");
        assertThat(capturedCommand[0].recording().sizeBytes()).isEqualTo(1_024L);

        /* 응답은 회의실·시작·종료 일시 없이 isOnline만 true로 고정돼야 한다. */
        assertThat(response.getData().isOnline()).isTrue();
        assertThat(response.getData().host().memberId()).isEqualTo(3L);
        assertThat(response.getData().attendees())
                .extracting(CreateOnlineMeetingResponse.AttendeeResponse::memberId)
                .containsExactly(3L, 7L, 11L);
    }

    /* Controller 대역이 반환할 완성된 온라인 회의 애플리케이션 결과를 만든다. */
    private OnlineMeetingCreationResult onlineResult() {
        /* 개설=입장=종료라 DONE 상태이며 회의실 없이 개설자와 참석자만 있는 온라인 회의 결과를 사용한다. */
        return new OnlineMeetingCreationResult(
                92L,
                MeetingStatus.DONE,
                "비대면 스프린트 회고",
                true,
                new OnlineMeetingCreationResult.Host(3L, "지우"),
                List.of(
                        new OnlineMeetingCreationResult.Attendee(3L, "지우", "기획"),
                        new OnlineMeetingCreationResult.Attendee(7L, "이든", "개발"),
                        new OnlineMeetingCreationResult.Attendee(11L, "하린", "디자인")
                )
        );
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
