package com.module06.backend.meetingroom.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.GlobalExceptionHandler;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.meetingroom.application.query.MeetingRoomAvailabilityQuery;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomAvailabilitySummary;
import com.module06.backend.meetingroom.application.result.MeetingRoomDayAvailability;
import com.module06.backend.meetingroom.application.result.MeetingRoomSlotSummary;
import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomAvailabilityUseCase;
import com.module06.backend.meetingroom.application.usecase.GetMeetingRoomListUseCase;
import com.module06.backend.meetingroom.presentation.api.response.MeetingRoomAvailabilityResponse;
import com.module06.backend.meetingroom.presentation.api.response.MeetingRoomListResponse;

/*
 * ROOM-01·ROOM-02 Controller의 성공 응답 변환을 검증하는 단위 테스트다.
 *
 * 인증 필터와 데이터베이스를 실행하지 않고 UseCase 대역을 주입해
 * 공통 응답 형식, 빈 배열, 날짜·시각 문자열 형식이 API 명세와 일치하는지 확인한다.
 */
@DisplayName("회의실 조회 Controller")
class MeetingRoomControllerTest {

    /* ROOM-02 검증에서 호출되지 않아야 하는 ROOM-01 UseCase 대역이다. */
    private static final GetMeetingRoomListUseCase EMPTY_LIST_USE_CASE = companyId -> List.of();

    /* ROOM-01 검증에서 호출되지 않아야 하는 ROOM-02 UseCase 대역이다. */
    private static final GetMeetingRoomAvailabilityUseCase UNUSED_AVAILABILITY_USE_CASE =
            query -> new MeetingRoomAvailability(
                    LocalDate.of(2026, 8, 10),
                    LocalDate.of(2026, 8, 14),
                    30,
                    new MeetingRoomAvailabilitySummary(
                            query.meetingRoomId(), "회의실"),
                    List.of()
            );

    /*
     * 조회된 회의실이 공통 응답과 ROOM-01 응답 DTO로 올바르게 변환되는지 검증한다.
     */
    @Test
    @DisplayName("ROOM-01 · 회의실 목록을 200 성공 응답으로 반환한다")
    void returnsMeetingRoomListResponse() {
        /* Controller에 반환할 애플리케이션 조회 결과를 준비한다. */
        GetMeetingRoomListUseCase useCase = companyId -> List.of(
                new MeetingRoomSummary(
                        1L,
                        "대회의실",
                        "박애관 421호"
                )
        );
        MeetingRoomController controller = new MeetingRoomController(useCase, UNUSED_AVAILABILITY_USE_CASE);

        /* 인증 principal에서 추출됐다고 가정한 회사 식별자로 API 메서드를 호출한다. */
        ApiResponse<MeetingRoomListResponse> response = controller.getMeetingRooms(10L);

        /* 공통 응답 상태와 메시지가 API 명세에 맞는지 확인한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의실 목록 조회에 성공했습니다.");

        /* 회의실 기본 정보가 API 명세대로 변환됐는지 확인한다. */
        assertThat(response.getData().meetingRooms()).hasSize(1);
        assertThat(response.getData().meetingRooms().get(0).meetingRoomId()).isEqualTo(1L);
        assertThat(response.getData().meetingRooms().get(0).name()).isEqualTo("대회의실");
    }

    /*
     * 조회 결과가 없을 때 meetingRooms가 null이 아닌 빈 목록으로 반환되는지 검증한다.
     */
    @Test
    @DisplayName("ROOM-01 · 회의실이 없으면 빈 배열을 반환한다")
    void returnsEmptyMeetingRoomList() {
        /* 빈 결과를 반환하는 UseCase 대역으로 Controller를 생성한다. */
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, UNUSED_AVAILABILITY_USE_CASE);

        /* 회의실이 없는 회사의 API 메서드를 호출한다. */
        ApiResponse<MeetingRoomListResponse> response = controller.getMeetingRooms(10L);

        /* 200 성공 상태를 유지하면서 빈 meetingRooms 목록이 반환되는지 확인한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().meetingRooms()).isEmpty();
    }

    /* 최신 권한 매트릭스대로 ADMIN도 ROOM-01 목록을 조회할 수 있는지 선언을 검증한다. */
    @Test
    @DisplayName("ROOM-01 · 모든 인증 역할에 목록 조회를 허용한다")
    void allowsAllAuthenticatedRolesToListMeetingRooms() throws NoSuchMethodException {
        /* 실제 ROOM-01 Controller 메서드에 선언된 역할 표현식을 조회한다. */
        PreAuthorize preAuthorize = MeetingRoomController.class
                .getDeclaredMethod("getMeetingRooms", Long.class)
                .getAnnotation(PreAuthorize.class);

        /* OWNER·ADMIN·LEADER·MEMBER가 모두 빠짐없이 포함돼야 한다. */
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("OWNER", "ADMIN", "LEADER", "MEMBER");
    }

    /*
     * 슬롯 현황이 공통 응답과 ROOM-02 응답 DTO로 올바르게 변환되는지 검증한다.
     */
    @Test
    @DisplayName("ROOM-02 · 슬롯 현황을 200 성공 응답으로 반환한다")
    void returnsMeetingRoomAvailabilityResponse() {
        /* 예약 가능 슬롯과 예약된 슬롯이 섞인 조회 결과를 준비한다. */
        GetMeetingRoomAvailabilityUseCase useCase = query -> new MeetingRoomAvailability(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 14),
                30,
                new MeetingRoomAvailabilitySummary(
                        2L,
                        "회의실 B"
                ),
                List.of(new MeetingRoomDayAvailability(
                        LocalDate.of(2026, 8, 10),
                        java.time.DayOfWeek.MONDAY,
                        List.of(
                                MeetingRoomSlotSummary.available(LocalTime.of(9, 0)),
                                MeetingRoomSlotSummary.reserved(LocalTime.of(14, 0), 91L, "A커머스 온보딩 킥오프"),
                                MeetingRoomSlotSummary.reserved(LocalTime.of(15, 0), 94L, null)
                        )
                ))
        );
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* 인증 principal 값과 Query Parameter 문자열로 API 메서드를 호출한다. */
        ApiResponse<MeetingRoomAvailabilityResponse> response =
                controller.getMeetingRoomAvailability(10L, 3L, "2026-08-10", "2");

        /* 공통 응답 상태와 메시지가 API 명세에 맞는지 확인한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의실 예약 현황 조회에 성공했습니다.");

        /* 조회 주간 범위와 슬롯 길이가 명세 형식으로 변환됐는지 확인한다. */
        assertThat(response.getData().weekStart()).isEqualTo("2026-08-10");
        assertThat(response.getData().weekEnd()).isEqualTo("2026-08-14");
        assertThat(response.getData().slotMinutes()).isEqualTo(30);

        /* 단일 회의실 정보와 월요일 슬롯 상태·제목이 명세대로 변환됐는지 확인한다. */
        assertThat(response.getData().meetingRoom().meetingRoomId()).isEqualTo(2L);
        assertThat(response.getData().days()).hasSize(1);
        assertThat(response.getData().days().get(0).date()).isEqualTo("2026-08-10");
        assertThat(response.getData().days().get(0).dayOfWeek()).isEqualTo("MON");
        assertThat(response.getData().days().get(0).slots()).hasSize(3);
        assertThat(response.getData().days().get(0).slots().get(0).startTime()).isEqualTo("09:00");
        assertThat(response.getData().days().get(0).slots().get(0).status()).isEqualTo("AVAILABLE");
        assertThat(response.getData().days().get(0).slots().get(0).meetingId()).isNull();
        assertThat(response.getData().days().get(0).slots().get(1).status()).isEqualTo("RESERVED");
        assertThat(response.getData().days().get(0).slots().get(1).meetingId()).isEqualTo(91L);
        assertThat(response.getData().days().get(0).slots().get(1).title())
                .isEqualTo("A커머스 온보딩 킥오프");

        /* 열람 권한이 없는 회의는 예약 사실만 남고 제목이 비어 있어야 한다. */
        assertThat(response.getData().days().get(0).slots().get(2).meetingId()).isEqualTo(94L);
        assertThat(response.getData().days().get(0).slots().get(2).title()).isNull();
    }

    /*
     * 인증 정보와 Query Parameter가 조회 조건으로 그대로 전달되는지 검증한다.
     */
    @Test
    @DisplayName("ROOM-02 · 인증 정보와 회의실 필터를 조회 조건으로 전달한다")
    void passesAuthenticationAndFilterToQuery() {
        /* UseCase가 받은 조회 조건을 확인할 수 있도록 배열에 보관한다. */
        MeetingRoomAvailabilityQuery[] captured = new MeetingRoomAvailabilityQuery[1];
        GetMeetingRoomAvailabilityUseCase useCase = query -> {
            captured[0] = query;
            return new MeetingRoomAvailability(
                    LocalDate.of(2026, 8, 3),
                    LocalDate.of(2026, 8, 7),
                    30,
                    new MeetingRoomAvailabilitySummary(
                            query.meetingRoomId(), "회의실"),
                    List.of()
            );
        };
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* 특정 회의실만 조회하는 요청을 실행한다. */
        controller.getMeetingRoomAvailability(10L, 3L, "2026-08-04", "2");

        /* 회사·구성원 식별자와 파싱된 조회 조건이 그대로 전달됐는지 확인한다. */
        assertThat(captured[0].companyId()).isEqualTo(10L);
        assertThat(captured[0].memberId()).isEqualTo(3L);
        assertThat(captured[0].date()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(captured[0].meetingRoomId()).isEqualTo(2L);
    }

    /*
     * 조회 날짜를 생략하면 null 기준일이 UseCase까지 전달되는지 검증한다.
     */
    @Test
    @DisplayName("ROOM-02 · date를 생략하면 서버 날짜를 사용하도록 null을 전달한다")
    void acceptsMissingDate() {
        /* UseCase가 받은 선택 기준일을 확인할 수 있도록 배열에 보관한다. */
        MeetingRoomAvailabilityQuery[] captured = new MeetingRoomAvailabilityQuery[1];
        GetMeetingRoomAvailabilityUseCase useCase = query -> {
            captured[0] = query;
            return new MeetingRoomAvailability(
                    LocalDate.of(2026, 8, 10),
                    LocalDate.of(2026, 8, 14),
                    30,
                    new MeetingRoomAvailabilitySummary(
                            query.meetingRoomId(), "회의실"),
                    List.of()
            );
        };
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* 날짜 없이 필수 회의실만 전달해 조회한다. */
        controller.getMeetingRoomAvailability(10L, 3L, null, "2");

        /* 서비스가 KST Clock을 적용할 수 있도록 기준일 null이 유지돼야 한다. */
        assertThat(captured[0].date()).isNull();
        assertThat(captured[0].meetingRoomId()).isEqualTo(2L);
    }

    /* 필수 회의실 식별자가 없으면 UseCase 호출 전에 입력값 오류로 끝나는지 검증한다. */
    @Test
    @DisplayName("ROOM-02 · meetingRoomId가 없으면 Z-001로 거절한다")
    void rejectsMissingMeetingRoomId() {
        /* 검증 실패 요청이 UseCase에 도달하면 테스트를 실패시키는 대역을 준비한다. */
        GetMeetingRoomAvailabilityUseCase useCase = query -> {
            throw new AssertionError("회의실 식별자가 없는 요청은 UseCase까지 도달하지 않아야 한다.");
        };
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* 필수 회의실 식별자 없이 호출하면 공통 입력값 오류가 발생해야 한다. */
        assertThatThrownBy(() -> controller.getMeetingRoomAvailability(10L, 3L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }

    /* 실제 HTTP 요청에서 필수 Query Parameter 누락이 500이 아닌 명세 오류로 변환되는지 검증한다. */
    @Test
    @DisplayName("ROOM-02 · HTTP 요청에서 meetingRoomId가 없으면 400 Z-001을 반환한다")
    void returnsBadRequestWhenMeetingRoomIdRequestParameterIsMissing() throws Exception {
        /* 검증 실패 요청이 UseCase에 도달하면 테스트를 실패시키는 대역을 준비한다. */
        GetMeetingRoomAvailabilityUseCase useCase = query -> {
            throw new AssertionError("회의실 식별자가 없는 HTTP 요청은 UseCase까지 도달하지 않아야 한다.");
        };
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* 전역 예외 처리기를 포함한 독립 MVC 환경으로 실제 파라미터 바인딩 경로를 재현한다. */
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        /* meetingRoomId를 생략한 요청은 DTO 검증을 거쳐 공통 입력값 오류로 응답돼야 한다. */
        mockMvc.perform(get("/api/rooms/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("Z-001"));
    }

    /* 기준일 형식이 잘못되면 UseCase 호출 전에 입력값 오류로 끝나는지 검증한다. */
    @Test
    @DisplayName("ROOM-02 · date 형식이 잘못되면 Z-001로 거절한다")
    void rejectsMalformedDate() {
        /* 검증 실패 요청이 UseCase에 도달하면 테스트를 실패시키는 대역을 준비한다. */
        GetMeetingRoomAvailabilityUseCase useCase = query -> {
            throw new AssertionError("형식이 잘못된 날짜는 UseCase까지 도달하지 않아야 한다.");
        };
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* ISO 날짜 형식이 아닌 값을 전달하면 공통 입력값 오류가 발생해야 한다. */
        assertThatThrownBy(() -> controller.getMeetingRoomAvailability(10L, 3L, "2026-8-10", "2"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }

    /* 회의실 식별자가 숫자가 아니거나 양수가 아니면 입력값 오류로 끝나는지 검증한다. */
    @Test
    @DisplayName("ROOM-02 · meetingRoomId 형식이 잘못되면 Z-001로 거절한다")
    void rejectsMalformedMeetingRoomId() {
        /* 검증 실패 요청이 UseCase에 도달하면 테스트를 실패시키는 대역을 준비한다. */
        GetMeetingRoomAvailabilityUseCase useCase = query -> {
            throw new AssertionError("형식이 잘못된 회의실 식별자는 UseCase까지 도달하지 않아야 한다.");
        };
        MeetingRoomController controller = new MeetingRoomController(EMPTY_LIST_USE_CASE, useCase);

        /* 비숫자와 음수 식별자가 모두 동일한 공통 입력값 오류로 변환되는지 확인한다. */
        assertThatThrownBy(() -> controller.getMeetingRoomAvailability(10L, 3L, null, "room"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
        assertThatThrownBy(() -> controller.getMeetingRoomAvailability(10L, 3L, null, "-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }
}
