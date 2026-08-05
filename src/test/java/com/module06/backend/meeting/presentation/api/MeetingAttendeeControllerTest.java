package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meeting.application.query.GetMeetingAttendeesQuery;
import com.module06.backend.meeting.application.result.MeetingAttendeesResult;
import com.module06.backend.meeting.application.usecase.GetMeetingAttendeesUseCase;
import com.module06.backend.meeting.presentation.api.response.MeetingAttendeeListResponse;

/*
 * RESULT-01 Controller의 인증 정보 전달과 A 화자 후보 응답 변환을 검증한다.
 */
@DisplayName("RESULT-01 회의 참석자 Controller")
class MeetingAttendeeControllerTest {

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
        MeetingAttendeeController controller = new MeetingAttendeeController(useCase);

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
}
