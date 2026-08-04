package com.module06.backend.meetingroom.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomListUseCase;
import com.module06.backend.meetingroom.presentation.api.response.MeetingRoomListResponse;

/*
 * ROOM-01 Controller의 성공 응답 변환을 검증하는 단위 테스트다.
 *
 * 인증 필터와 데이터베이스를 실행하지 않고 UseCase 대역을 주입해
 * 공통 응답 형식, 빈 배열, HH:mm 시간 형식이 API 명세와 일치하는지 확인한다.
 */
@DisplayName("ROOM-01 회의실 목록 Controller")
class MeetingRoomControllerTest {

    /*
     * 조회된 회의실이 공통 응답과 ROOM-01 응답 DTO로 올바르게 변환되는지 검증한다.
     */
    @Test
    @DisplayName("회의실 목록을 200 성공 응답으로 반환한다")
    void returnsMeetingRoomListResponse() {
        /* Controller에 반환할 애플리케이션 조회 결과를 준비한다. */
        GetMeetingRoomListUseCase useCase = companyId -> List.of(
                new MeetingRoomSummary(
                        1L,
                        "대회의실",
                        "박애관 421호",
                        12,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )
        );
        MeetingRoomController controller = new MeetingRoomController(useCase);

        /* 인증 principal에서 추출됐다고 가정한 회사 식별자로 API 메서드를 호출한다. */
        ApiResponse<MeetingRoomListResponse> response = controller.getMeetingRooms(10L);

        /* 공통 응답 상태와 메시지가 API 명세에 맞는지 확인한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의실 목록 조회에 성공했습니다.");

        /* 회의실 기본 정보와 시각 문자열이 API 명세대로 변환됐는지 확인한다. */
        assertThat(response.getData().meetingRooms()).hasSize(1);
        assertThat(response.getData().meetingRooms().get(0).meetingRoomId()).isEqualTo(1L);
        assertThat(response.getData().meetingRooms().get(0).name()).isEqualTo("대회의실");
        assertThat(response.getData().meetingRooms().get(0).availableFrom()).isEqualTo("09:00");
        assertThat(response.getData().meetingRooms().get(0).availableTo()).isEqualTo("18:00");
    }

    /*
     * 조회 결과가 없을 때 meetingRooms가 null이 아닌 빈 목록으로 반환되는지 검증한다.
     */
    @Test
    @DisplayName("회의실이 없으면 빈 배열을 반환한다")
    void returnsEmptyMeetingRoomList() {
        /* 빈 결과를 반환하는 UseCase 대역으로 Controller를 생성한다. */
        GetMeetingRoomListUseCase useCase = companyId -> List.of();
        MeetingRoomController controller = new MeetingRoomController(useCase);

        /* 회의실이 없는 회사의 API 메서드를 호출한다. */
        ApiResponse<MeetingRoomListResponse> response = controller.getMeetingRooms(10L);

        /* 200 성공 상태를 유지하면서 빈 meetingRooms 목록이 반환되는지 확인한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().meetingRooms()).isEmpty();
    }
}
