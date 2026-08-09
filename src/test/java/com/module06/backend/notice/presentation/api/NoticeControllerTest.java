package com.module06.backend.notice.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.notice.application.query.GetNoticeDetailQuery;
import com.module06.backend.notice.application.command.CreateNoticeCommand;
import com.module06.backend.notice.application.result.NoticeCreationResult;
import com.module06.backend.notice.application.query.GetNoticeListQuery;
import com.module06.backend.notice.application.result.NoticeDetailResult;
import com.module06.backend.notice.application.result.NoticeListResult;
import com.module06.backend.notice.application.usecase.GetNoticeDetailUseCase;
import com.module06.backend.notice.application.usecase.GetNoticeListUseCase;
import com.module06.backend.notice.application.usecase.CreateNoticeUseCase;
import com.module06.backend.notice.presentation.api.request.CreateNoticeRequest;
import com.module06.backend.notice.presentation.api.response.NoticeListResponse;

/* NOTI-01~03 Controller의 인증 정보 전달과 외부 성공·검증 응답 변환을 확인한다. */
@DisplayName("공지 조회·작성 Controller")
class NoticeControllerTest {

    /* principal의 회사가 Query에 들어가고 공지 목록이 명세 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("인증 회사의 공지 목록을 200 공통 응답으로 반환한다")
    void returnsNoticeListForAuthenticatedCompany() {
        /* Controller가 전달한 Query를 기록하고 공지 한 건을 반환하는 유스케이스 대역을 만든다. */
        GetNoticeListQuery[] capturedQuery = new GetNoticeListQuery[1];
        GetNoticeListUseCase useCase = query -> {
            capturedQuery[0] = query;
            return new NoticeListResult(List.of(new NoticeListResult.NoticeItem(
                    1L,
                    "회의실 예약과 참석 안내",
                    LocalDateTime.of(2026, 8, 3, 10, 12)
            )));
        };
        NoticeController controller = new NoticeController(
                useCase,
                unusedDetailUseCase(),
                unusedCreateUseCase()
        );
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);

        /* 요청 회사 파라미터 없이 인증 principal만으로 Controller 메서드를 호출한다. */
        ApiResponse<NoticeListResponse> response = controller.getNotices(principal);

        /* 인증 회사 식별자가 변형 없이 애플리케이션 Query로 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);

        /* 명세의 200 상태·메시지·초 단위 생성 일시를 포함한 목록을 반환해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("공지 목록 조회에 성공했습니다.");
        assertThat(response.getData().notices()).hasSize(1);
        assertThat(response.getData().notices().get(0).noticeId()).isEqualTo(1L);
        assertThat(response.getData().notices().get(0).createdAt()).isEqualTo("2026-08-03T10:12:00");
    }

    /* 유스케이스 빈 결과가 null이 아닌 빈 배열 응답으로 유지되는지 검증한다. */
    @Test
    @DisplayName("공지가 없으면 빈 notices 배열을 반환한다")
    void returnsEmptyNoticeArray() {
        /* 빈 공지 결과를 반환하는 유스케이스로 Controller를 구성한다. */
        NoticeController controller = new NoticeController(
                query -> new NoticeListResult(List.of()),
                unusedDetailUseCase(),
                unusedCreateUseCase()
        );
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);

        /* 공지가 없는 회사의 목록을 조회한다. */
        ApiResponse<NoticeListResponse> response = controller.getNotices(principal);

        /* HTTP 200과 함께 직렬화 가능한 빈 notices 목록이 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().notices()).isEmpty();
    }

    /* 인증 회사와 경로 공지 식별자가 전달되고 상세 응답이 변환되는지 검증한다. */
    @Test
    @DisplayName("공지 상세를 200 공통 응답으로 반환한다")
    void returnsNoticeDetailForAuthenticatedCompany() {
        /* 상세 Query를 기록하고 수정 전 공지 결과를 반환하는 유스케이스 대역을 만든다. */
        GetNoticeDetailQuery[] capturedQuery = new GetNoticeDetailQuery[1];
        GetNoticeDetailUseCase detailUseCase = query -> {
            capturedQuery[0] = query;
            return new NoticeDetailResult(
                    1L,
                    "회의실 예약과 참석 안내",
                    "회의는 회의실 예약 화면에서만 개설할 수 있습니다.",
                    LocalDateTime.of(2026, 8, 3, 10, 12),
                    null
            );
        };
        NoticeController controller = new NoticeController(
                query -> new NoticeListResult(List.of()),
                detailUseCase,
                unusedCreateUseCase()
        );
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);

        /* 인증 principal과 경로 공지 1로 상세 Controller 메서드를 호출한다. */
        var response = controller.getNotice(principal, 1L);

        /* 인증 회사와 경로 식별자가 요청값 추가 없이 Query에 전달돼야 한다. */
        assertThat(capturedQuery[0].companyId()).isEqualTo(10L);
        assertThat(capturedQuery[0].noticeId()).isEqualTo(1L);

        /* 명세 메시지·본문·초 단위 생성 시각과 null 수정 시각이 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("공지 조회에 성공했습니다.");
        assertThat(response.getData().content())
                .isEqualTo("회의는 회의실 예약 화면에서만 개설할 수 있습니다.");
        assertThat(response.getData().createdAt()).isEqualTo("2026-08-03T10:12:00");
        assertThat(response.getData().updatedAt()).isNull();
    }

    /* 인증 principal과 요청 본문이 작성 Command로 전달되고 201 본문이 반환되는지 검증한다. */
    @Test
    @DisplayName("OWNER가 공지를 작성하고 생성 식별자를 반환한다")
    void createsNoticeWithAuthenticatedPrincipal() {
        /* 작성 Command를 기록하고 생성된 공지 식별자 31을 반환하는 유스케이스 대역을 만든다. */
        CreateNoticeCommand[] capturedCommand = new CreateNoticeCommand[1];
        CreateNoticeUseCase createUseCase = command -> {
            capturedCommand[0] = command;
            return new NoticeCreationResult(31L);
        };
        NoticeController controller = new NoticeController(
                query -> new NoticeListResult(List.of()),
                unusedDetailUseCase(),
                createUseCase
        );
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "OWNER", false, null);

        /* 인증 OWNER와 정상 제목·본문으로 Controller 작성 메서드를 호출한다. */
        var response = controller.createNotice(
                principal,
                new CreateNoticeRequest("회의실 예약과 참석 안내", "공지 본문")
        );

        /* 회사·작성자·역할은 요청이 아니라 인증 principal 값으로 Command에 들어가야 한다. */
        assertThat(capturedCommand[0].companyId()).isEqualTo(10L);
        assertThat(capturedCommand[0].requesterMemberId()).isEqualTo(3L);
        assertThat(capturedCommand[0].requesterRole()).isEqualTo("OWNER");

        /* 공통 생성 응답 본문은 201 상태와 생성된 공지 식별자만 포함해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("공지를 등록했습니다.");
        assertThat(response.getData().noticeId()).isEqualTo(31L);
    }

    /* NOTI-03 메서드가 실제 201 상태와 OWNER·ADMIN 권한을 선언하는지 검증한다. */
    @Test
    @DisplayName("공지 작성은 OWNER·ADMIN에게만 열리고 실제 HTTP 201을 선언한다")
    void declaresCreateNoticeAuthorizationAndStatus() throws NoSuchMethodException {
        /* Controller 작성 메서드의 권한과 응답 상태 애노테이션을 조회한다. */
        var method = NoticeController.class.getDeclaredMethod(
                "createNotice",
                AuthPrincipal.class,
                CreateNoticeRequest.class
        );
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        /* 작성 권한은 OWNER·ADMIN만 포함하고 실제 HTTP 상태는 201이어야 한다. */
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("OWNER", "ADMIN").doesNotContain("LEADER", "MEMBER");
        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value().value()).isEqualTo(201);
    }

    /* 공지 요청 Bean Validation 실패가 필드 details를 가진 NT-003 응답인지 검증한다. */
    @Test
    @DisplayName("잘못된 공지 작성 본문을 NT-003으로 반환한다")
    void returnsNoticeValidationError() throws Exception {
        /* 잘못된 요청이 유스케이스에 도달하면 실패하는 Controller와 공지 전용 Advice를 구성한다. */
        NoticeController controller = new NoticeController(
                query -> new NoticeListResult(List.of()),
                unusedDetailUseCase(),
                command -> {
                    throw new AssertionError("검증 실패 요청은 작성 UseCase까지 전달되면 안 됩니다.");
                }
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new NoticeValidationExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        /* 공백 제목과 본문을 전송해 공지 전용 검증 응답을 요청한다. */
        mockMvc.perform(post("/api/v1/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","content":" "}
                                """))
                /* 공통 Z-001이 아니라 NT-003과 두 필드의 details가 반환돼야 한다. */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NT-003"))
                .andExpect(jsonPath("$.details[*].field")
                        .value(containsInAnyOrder("title", "content")));
    }

    /* 목록 Controller 테스트에서 사용하지 않는 상세 유스케이스 대역을 만든다. */
    private GetNoticeDetailUseCase unusedDetailUseCase() {
        /* 잘못 상세 호출되면 테스트를 즉시 실패시켜 Controller 경로 분리를 검증한다. */
        return query -> {
            throw new AssertionError("공지 목록 조회에서 상세 UseCase를 호출하면 안 됩니다.");
        };
    }

    /* 조회 Controller 테스트에서 사용하지 않는 작성 유스케이스 대역을 만든다. */
    private CreateNoticeUseCase unusedCreateUseCase() {
        /* 잘못 작성 호출되면 테스트를 즉시 실패시켜 Controller 경로 분리를 검증한다. */
        return command -> {
            throw new AssertionError("공지 조회에서 작성 UseCase를 호출하면 안 됩니다.");
        };
    }
}
