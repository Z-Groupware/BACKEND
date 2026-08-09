package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.UpdateMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingUpdateResult;
import com.module06.backend.meeting.application.usecase.UpdateMeetingUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.request.UpdateMeetingRequest;
import com.module06.backend.meeting.presentation.api.response.UpdateMeetingResponse;

/*
 * MEET-05 Controller의 인증 principal 전달과 PATCH 필드 존재 및 외부 응답 계약을 검증한다.
 */
@DisplayName("MEET-05 회의 정보 수정 Controller")
class MeetingUpdateControllerTest {

    /* 요청 DTO 필드 제약을 실제 Bean Validation으로 확인하는 검증기다. */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /* 인증 값과 제공된 PATCH 필드가 Command로 전달되고 200 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("제공된 시간 필드만 인증 범위와 결합해 회의를 수정한다")
    void updatesProvidedMeetingFields() {
        /* Controller가 만든 Command를 기록할 한 칸짜리 공간을 준비한다. */
        UpdateMeetingCommand[] capturedCommand = new UpdateMeetingCommand[1];

        /* Command를 기록하고 수정 완료 결과를 반환하는 유스케이스 대역을 만든다. */
        UpdateMeetingUseCase useCase = command -> {
            capturedCommand[0] = command;
            return new MeetingUpdateResult(
                    91L,
                    MeetingStatus.SCHEDULED,
                    LocalDateTime.of(2026, 8, 8, 15, 0),
                    LocalDateTime.of(2026, 8, 8, 16, 0),
                    new MeetingUpdateResult.MeetingRoom(2L, "회의실 B")
            );
        };
        MeetingUpdateController controller = new MeetingUpdateController(useCase);

        /* JSON에서 startAt과 endAt만 전달된 것과 같은 요청 객체를 준비한다. */
        UpdateMeetingRequest request = new UpdateMeetingRequest();
        request.setStartAt(LocalDateTime.of(2026, 8, 8, 15, 0));
        request.setEndAt(LocalDateTime.of(2026, 8, 8, 16, 0));

        /* 회사·구성원·권한·팀이 담긴 host principal과 Path 식별자로 Controller를 호출한다. */
        ApiResponse<UpdateMeetingResponse> response = controller.updateMeeting(
                new AuthPrincipal(3L, 10L, "MEMBER", false, 100L),
                91L,
                request
        );

        /* principal과 Path 값은 조작 가능한 요청 본문과 무관하게 Command에 전달돼야 한다. */
        assertThat(capturedCommand[0].companyId()).isEqualTo(10L);
        assertThat(capturedCommand[0].requesterMemberId()).isEqualTo(3L);
        assertThat(capturedCommand[0].requesterRole()).isEqualTo("MEMBER");
        assertThat(capturedCommand[0].meetingId()).isEqualTo(91L);

        /* 전달된 두 필드만 provided이고 나머지는 미전달 상태여야 한다. */
        assertThat(capturedCommand[0].startAtProvided()).isTrue();
        assertThat(capturedCommand[0].endAtProvided()).isTrue();
        assertThat(capturedCommand[0].titleProvided()).isFalse();
        assertThat(capturedCommand[0].projectIdProvided()).isFalse();
        assertThat(capturedCommand[0].meetingRoomIdProvided()).isFalse();
        assertThat(capturedCommand[0].recordingConsentProvided()).isFalse();

        /* 공통 200 상태와 성공 메시지 및 초 단위 KST 일시가 응답 계약과 일치해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의 정보를 수정했습니다.");
        assertThat(response.getData().status()).isEqualTo("SCHEDULED");
        assertThat(response.getData().startAt()).isEqualTo("2026-08-08T15:00:00");
        assertThat(response.getData().endAt()).isEqualTo("2026-08-08T16:00:00");
        assertThat(response.getData().meetingRoom().name()).isEqualTo("회의실 B");
    }

    /* 명시적 null과 미전달이 PATCH Command에서 다르게 보존되는지 검증한다. */
    @Test
    @DisplayName("명시적 null 녹음 동의는 미전달과 구분해 서비스로 전달한다")
    void preservesExplicitNullPatchValue() {
        /* 녹음 동의를 JSON null로 전달한 것과 같은 요청 객체를 만든다. */
        UpdateMeetingRequest request = new UpdateMeetingRequest();
        request.setRecordingConsent(null);

        /* 요청 객체를 principal·Path 값과 결합한 Command로 변환한다. */
        UpdateMeetingCommand command = request.toCommand(10L, 3L, "MEMBER", false, 91L);

        /* 필드는 제공됐지만 값은 null인 상태가 서비스 입력 검증까지 손실 없이 전달돼야 한다. */
        assertThat(command.recordingConsentProvided()).isTrue();
        assertThat(command.recordingConsent()).isNull();
        assertThat(command.titleProvided()).isFalse();
    }

    /* 프레젠테이션 경계의 길이·양수 제약이 잘못된 값을 수집하는지 검증한다. */
    @Test
    @DisplayName("긴 제목과 음수 식별자는 요청 검증 단계에서 거절한다")
    void rejectsInvalidPatchFieldsAtRequestBoundary() {
        /* DB 길이를 넘는 제목과 음수 프로젝트 식별자를 명시적으로 제공한다. */
        UpdateMeetingRequest request = new UpdateMeetingRequest();
        request.setTitle("가".repeat(201));
        request.setProjectId(-1L);

        /* 실제 Bean Validation으로 두 필드 제약을 실행한다. */
        var violations = validator.validate(request);

        /* @Size와 @Positive 위반이 각각 하나씩 수집돼 공통 400 details로 이어져야 한다. */
        assertThat(violations).hasSize(2);
    }
}
