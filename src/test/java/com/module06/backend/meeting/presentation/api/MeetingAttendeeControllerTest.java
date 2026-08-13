package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.command.ReplaceMeetingAttendeesCommand;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;
import com.module06.backend.meeting.application.usecase.ReplaceMeetingAttendeesUseCase;
import com.module06.backend.meeting.presentation.api.request.ReplaceMeetingAttendeesRequest;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeUpdateResponse;

/*
 * MEET-09 참석자 명단 교체 Controller의 인증 정보 전달과 응답 변환을 검증한다.
 */
@DisplayName("MEET-09 참석자 명단 교체 Controller")
class MeetingAttendeeControllerTest {

    /* 요청 DTO 요소 제약을 단위 테스트에서 실행할 Bean Validation 검증기다. */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /* 인증 정보와 Path 및 요청 명단이 MEET-09 Command로 전달되는지 검증한다. */
    @Test
    @DisplayName("교체된 참석자 전체 명단을 200 응답으로 반환한다")
    void replacesMeetingAttendeeRoster() {
        /* 교체 유스케이스에 전달된 Command를 기록할 공간을 준비한다. */
        ReplaceMeetingAttendeesCommand[] capturedCommand = new ReplaceMeetingAttendeesCommand[1];

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
        MeetingAttendeeController controller = new MeetingAttendeeController(replaceUseCase);

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
