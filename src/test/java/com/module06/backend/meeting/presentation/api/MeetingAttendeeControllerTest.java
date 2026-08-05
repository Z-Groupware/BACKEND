package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.query.GetMeetingAttendeesQuery;
import com.module06.backend.meeting.application.command.ReplaceMeetingAttendeesCommand;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;
import com.module06.backend.meeting.application.result.MeetingAttendeesResult;
import com.module06.backend.meeting.application.usecase.GetMeetingAttendeesUseCase;
import com.module06.backend.meeting.application.usecase.ReplaceMeetingAttendeesUseCase;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeListResponse;
import com.module06.backend.meeting.presentation.api.request.ReplaceMeetingAttendeesRequest;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeUpdateResponse;

/*
 * RESULT-01 Controller의 인증 정보 전달과 A 화자 후보 응답 변환을 검증한다.
 */
@DisplayName("RESULT-01 회의 참석자 Controller")
class MeetingAttendeeControllerTest {

    /* 요청 DTO 요소 제약을 단위 테스트에서 실행할 Bean Validation 검증기다. */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /* 인증 principal과 Path 값이 조회 조건이 되고 personKey 응답이 생성되는지 확인한다. */
    @Test
    @DisplayName("참석자 명단을 personKey가 포함된 200 응답으로 반환한다")
    void returnsMeetingAttendeeRoster() {
        /* 유스케이스에 전달된 조회 조건을 기록할 공간을 준비한다. */
        GetMeetingAttendeesQuery[] capturedQuery = new GetMeetingAttendeesQuery[1];

        /* 조회 조건을 기록하고 개설자와 참석자 결과를 반환하는 유스케이스 대역을 만든다. */
        GetMeetingAttendeesUseCase useCase = query -> {
            capturedQuery[0] = query;
            return new MeetingAttendeesResult(
                    91L,
                    List.of(
                            new MeetingAttendeesResult.Attendee(3L, "지우", "기획", true),
                            new MeetingAttendeesResult.Attendee(7L, "이든", "개발", false)
                    )
            );
        };
        /* GET 테스트에서 호출되면 실패하는 MEET-09 유스케이스 대역을 준비한다. */
        ReplaceMeetingAttendeesUseCase replaceUseCase = command -> {
            throw new AssertionError("RESULT-01 조회에서는 교체 유스케이스를 호출하면 안 됩니다.");
        };
        MeetingAttendeeController controller = new MeetingAttendeeController(useCase, replaceUseCase);

        /* 인증 principal 값과 Path 회의 식별자로 Controller 메서드를 호출한다. */
        ApiResponse<MeetingAttendeeListResponse> response = controller.getMeetingAttendees(
                10L,
                7L,
                "MEMBER",
                false,
                91L
        );

        /* 프로젝트 공통 성공 래퍼가 200 상태와 RESULT-01 메시지를 가져야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의 참석자 조회에 성공했습니다.");

        /* 인증과 Path 값이 애플리케이션 조회 조건에 그대로 전달돼야 한다. */
        assertThat(capturedQuery[0]).isEqualTo(
                new GetMeetingAttendeesQuery(10L, 7L, "MEMBER", false, 91L)
        );

        /* 실제 구성원 응답에는 A 화자 매핑 키와 MEMBER 타입이 포함돼야 한다. */
        assertThat(response.getData().attendees().get(0).personKey()).isEqualTo("member:3");
        assertThat(response.getData().attendees().get(0).type()).isEqualTo("MEMBER");
        assertThat(response.getData().attendees().get(0).isHost()).isTrue();
        assertThat(response.getData().attendees().get(1).personKey()).isEqualTo("member:7");
        assertThat(response.getData().attendees().get(1).isHost()).isFalse();
    }

    /* 인증 정보와 Path 및 요청 명단이 MEET-09 Command로 전달되는지 검증한다. */
    @Test
    @DisplayName("교체된 참석자 전체 명단을 200 응답으로 반환한다")
    void replacesMeetingAttendeeRoster() {
        /* 교체 유스케이스에 전달된 Command를 기록할 공간을 준비한다. */
        ReplaceMeetingAttendeesCommand[] capturedCommand = new ReplaceMeetingAttendeesCommand[1];

        /* GET이 호출되면 실패하는 RESULT-01 유스케이스 대역을 준비한다. */
        GetMeetingAttendeesUseCase getUseCase = query -> {
            throw new AssertionError("MEET-09 교체에서는 조회 유스케이스를 호출하면 안 됩니다.");
        };

        /* Command를 기록하고 host 포함 최종 명단 결과를 반환하는 교체 유스케이스 대역을 만든다. */
        ReplaceMeetingAttendeesUseCase replaceUseCase = command -> {
            capturedCommand[0] = command;
            return new MeetingAttendeeUpdateResult(
                    91L,
                    List.of(
                            new MeetingAttendeeUpdateResult.Attendee(3L, "지우", "기획"),
                            new MeetingAttendeeUpdateResult.Attendee(7L, "이든", "개발"),
                            new MeetingAttendeeUpdateResult.Attendee(11L, "하린", "디자인")
                    )
            );
        };
        MeetingAttendeeController controller = new MeetingAttendeeController(getUseCase, replaceUseCase);

        /* host를 생략한 요청 명단과 인증 principal 값으로 PUT 메서드를 호출한다. */
        ReplaceMeetingAttendeesRequest request = new ReplaceMeetingAttendeesRequest(List.of(7L, 11L));
        ApiResponse<MeetingAttendeeUpdateResponse> response = controller.replaceMeetingAttendees(
                10L,
                3L,
                "MEMBER",
                false,
                91L,
                request
        );

        /* 인증·Path·본문 값이 조작 없이 MEET-09 Command로 결합돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new ReplaceMeetingAttendeesCommand(
                10L,
                3L,
                "MEMBER",
                false,
                91L,
                List.of(7L, 11L)
        ));

        /* 공통 200 상태와 명세 성공 메시지가 응답에 포함돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("참석자 명단을 변경했습니다.");

        /* 응답에는 host가 첫 번째인 최종 참석자 전체 명단이 포함돼야 한다. */
        assertThat(response.getData().attendees())
                .extracting(MeetingAttendeeUpdateResponse.AttendeeResponse::memberId)
                .containsExactly(3L, 7L, 11L);
    }

    /* null·0·음수 요소가 DTO 생성 중 NPE가 아니라 Bean Validation 오류로 수집되는지 검증한다. */
    @Test
    @DisplayName("잘못된 참석자 식별자를 요청 검증 단계에서 모두 거절한다")
    void rejectsInvalidAttendeeIdsAtRequestBoundary() {
        /* null 요소를 포함하는 변경 가능한 입력 목록으로 요청 DTO를 정상 생성한다. */
        ReplaceMeetingAttendeesRequest request = new ReplaceMeetingAttendeesRequest(
                java.util.Arrays.asList(7L, null, 0L, -1L)
        );

        /* 목록 요소의 @NotNull과 @Positive 제약을 실제 Bean Validation으로 실행한다. */
        var violations = validator.validate(request);

        /* null·0·음수 세 값이 각각 400 입력 오류의 필드 상세로 이어질 제약 위반이어야 한다. */
        assertThat(violations)
                .extracting(ConstraintViolation::getInvalidValue)
                .containsExactlyInAnyOrder(null, 0L, -1L);
    }
}
