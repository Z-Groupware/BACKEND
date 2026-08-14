package com.module06.backend.meetingroom.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meetingroom.application.command.CreateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;
import com.module06.backend.meetingroom.presentation.api.request.CreateMeetingRoomRequest;
import com.module06.backend.meetingroom.presentation.api.response.CreateMeetingRoomResponse;

/*
 * ROOM-03 Controller의 인증 회사 전달, 요청 변환, 201 응답을 검증하는 단위 테스트다.
 */
@DisplayName("ROOM-03 회의실 등록 Controller")
class MeetingRoomCommandControllerTest {

    /* ROOM-03 테스트에서 호출되면 실패하는 ROOM-04 수정 유스케이스 대역이다. */
    private static final UpdateMeetingRoomUseCase UNUSED_UPDATE_USE_CASE = command -> {
        throw new AssertionError("ROOM-03 등록에서는 수정 유스케이스를 호출하면 안 됩니다.");
    };

    /* ROOM-03 테스트에서 호출되면 실패하는 ROOM-05 비활성화 유스케이스 대역이다. */
    private static final DeactivateMeetingRoomUseCase UNUSED_DEACTIVATE_USE_CASE = command -> {
        throw new AssertionError("ROOM-03 등록에서는 비활성화 유스케이스를 호출하면 안 됩니다.");
    };

    /* 요청 DTO 제약을 실제 Bean Validation으로 확인하는 검증기다. */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /* 인증 회사와 요청 본문이 명령으로 변환되고 생성 응답이 반환되는지 검증한다. */
    @Test
    @DisplayName("인증 회사에 회의실을 등록하고 201 응답을 반환한다")
    void returnsCreatedMeetingRoomResponse() {
        /* 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        CreateMeetingRoomCommand[] capturedCommand = new CreateMeetingRoomCommand[1];

        /* 명령을 기록하고 데이터베이스 생성 식별자를 반환하는 유스케이스 대역을 만든다. */
        CreateMeetingRoomUseCase useCase = command -> {
            capturedCommand[0] = command;
            return new MeetingRoomCreationResult(101L);
        };
        MeetingRoomCommandController controller = new MeetingRoomCommandController(
                useCase,
                UNUSED_UPDATE_USE_CASE,
                UNUSED_DEACTIVATE_USE_CASE
        );

        /* 인증 principal의 회사 식별자와 명세 형식의 본문으로 Controller 메서드를 호출한다. */
        CreateMeetingRoomRequest request = new CreateMeetingRoomRequest(
                "대회의실",
                "박애관 421호"
        );
        ApiResponse<CreateMeetingRoomResponse> response = controller.createMeetingRoom(10L, request);

        /* 본문에 없는 회사 식별자가 애플리케이션 명령에 포함돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new CreateMeetingRoomCommand(
                10L,
                "대회의실",
                "박애관 421호"
        ));

        /* 공통 응답 본문은 201 상태, 명세 메시지, 생성 식별자를 가져야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("회의실을 등록했습니다.");
        assertThat(response.getData().meetingRoomId()).isEqualTo(101L);
    }

    /* 실제 HTTP 상태도 응답 본문과 동일한 201인지 검증한다. */
    @Test
    @DisplayName("등록 엔드포인트의 HTTP 상태를 201 Created로 선언한다")
    void declaresCreatedHttpStatus() throws NoSuchMethodException {
        /* ROOM-03 메서드에서 ResponseStatus 선언을 조회한다. */
        ResponseStatus responseStatus = MeetingRoomCommandController.class
                .getDeclaredMethod("createMeetingRoom", Long.class, CreateMeetingRoomRequest.class)
                .getAnnotation(ResponseStatus.class);

        /* 선언 누락이나 200 기본값 회귀 없이 201 Created여야 한다. */
        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.CREATED);
    }

    /* 요청 DTO가 빈 이름을 입구에서 거절하는지 검증한다. */
    @Test
    @DisplayName("잘못된 등록 본문을 Bean Validation 단계에서 거절한다")
    void rejectsInvalidRequestBody() {
        /* 빈 이름을 가진 요청을 준비한다. */
        CreateMeetingRoomRequest request = new CreateMeetingRoomRequest(
                " ",
                "박애관 421호"
        );

        /* 각 제약 위반의 속성 경로를 수집해 입력 경계 계약을 확인한다. */
        assertThat(validator.validate(request))
                .extracting(violation -> ((ConstraintViolation<CreateMeetingRoomRequest>) violation)
                        .getPropertyPath().toString())
                .containsExactly("name");
    }
}
