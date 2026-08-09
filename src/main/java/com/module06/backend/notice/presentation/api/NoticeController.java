package com.module06.backend.notice.presentation.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.notice.application.result.NoticeCreationResult;
import com.module06.backend.notice.application.result.NoticeUpdateResult;
import com.module06.backend.notice.application.query.GetNoticeDetailQuery;
import com.module06.backend.notice.application.query.GetNoticeListQuery;
import com.module06.backend.notice.application.result.NoticeDetailResult;
import com.module06.backend.notice.application.result.NoticeListResult;
import com.module06.backend.notice.application.usecase.GetNoticeDetailUseCase;
import com.module06.backend.notice.application.usecase.GetNoticeListUseCase;
import com.module06.backend.notice.application.usecase.CreateNoticeUseCase;
import com.module06.backend.notice.application.usecase.UpdateNoticeUseCase;
import com.module06.backend.notice.presentation.api.request.CreateNoticeRequest;
import com.module06.backend.notice.presentation.api.request.UpdateNoticeRequest;
import com.module06.backend.notice.presentation.api.response.CreateNoticeResponse;
import com.module06.backend.notice.presentation.api.response.NoticeDetailResponse;
import com.module06.backend.notice.presentation.api.response.NoticeListResponse;
import com.module06.backend.notice.presentation.api.response.UpdateNoticeResponse;

/* NOTI-01~04 회사 공지 조회·작성·수정 REST API의 진입점이다. */
@Tag(name = "Notice", description = "회사 공지 조회 및 관리 API")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    /* 공지 목록 프레젠테이션 계층과 조회 서비스를 연결하는 인바운드 Port다. */
    private final GetNoticeListUseCase getNoticeListUseCase;

    /* 공지 상세 프레젠테이션 계층과 조회 서비스를 연결하는 인바운드 Port다. */
    private final GetNoticeDetailUseCase getNoticeDetailUseCase;

    /* 공지 작성 프레젠테이션 계층과 명령 서비스를 연결하는 인바운드 Port다. */
    private final CreateNoticeUseCase createNoticeUseCase;

    /* 공지 수정 프레젠테이션 계층과 명령 서비스를 연결하는 인바운드 Port다. */
    private final UpdateNoticeUseCase updateNoticeUseCase;

    /* 인증 사용자의 회사에 속한 활성 공지를 최신순으로 조회한다. */
    @Operation(summary = "공지 목록 조회", description = "로그인한 사용자의 회사 공지를 최신순으로 조회합니다.")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping
    public ApiResponse<NoticeListResponse> getNotices(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal
    ) {
        /* 요청에서 회사 식별자를 받지 않고 인증 principal의 회사만 Query에 전달한다. */
        NoticeListResult result = getNoticeListUseCase.getNotices(
                new GetNoticeListQuery(principal.getCompanyId())
        );

        /* 빈 결과도 notices 빈 배열을 포함하는 공통 200 성공 응답으로 반환한다. */
        return ApiResponse.success(
                "공지 목록 조회에 성공했습니다.",
                NoticeListResponse.from(result)
        );
    }

    /* 인증 사용자의 회사 범위에서 삭제되지 않은 공지 한 건을 조회한다. */
    @Operation(summary = "공지 상세 조회", description = "공지 제목과 본문 및 생성·수정 시각을 조회합니다.")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'LEADER', 'MEMBER')")
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNotice(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "공지 식별자", example = "1") @PathVariable Long noticeId
    ) {
        /* 인증 회사와 경로 식별자를 Query로 묶어 NOTI-02 유스케이스를 실행한다. */
        NoticeDetailResult result = getNoticeDetailUseCase.getNotice(
                new GetNoticeDetailQuery(principal.getCompanyId(), noticeId)
        );

        /* 상세 결과를 외부 일시 문자열 계약으로 변환해 공통 200 응답으로 반환한다. */
        return ApiResponse.success(
                "공지 조회에 성공했습니다.",
                NoticeDetailResponse.from(result)
        );
    }

    /* OWNER·ADMIN이 인증된 자기 회사에 새로운 공지를 작성한다. */
    @Operation(summary = "공지 작성", description = "관리자가 로그인한 회사에 제목과 본문을 가진 공지를 작성합니다.")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CreateNoticeResponse> createNotice(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateNoticeRequest request
    ) {
        /* 인증 회사·작성자·역할과 검증된 요청 본문을 결합해 NOTI-03 유스케이스를 실행한다. */
        NoticeCreationResult result = createNoticeUseCase.createNotice(request.toCommand(
                principal.getCompanyId(),
                principal.getMemberId(),
                principal.getAuthority()
        ));

        /* 실제 HTTP와 응답 본문의 상태를 모두 201로 맞추고 생성 식별자만 반환한다. */
        return ApiResponse.created(
                "공지를 등록했습니다.",
                CreateNoticeResponse.from(result)
        );
    }

    /* OWNER·ADMIN이 인증된 자기 회사의 활성 공지 제목과 본문을 전체 수정한다. */
    @Operation(summary = "공지 수정", description = "관리자가 공지의 제목과 본문을 전체 수정합니다.")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PutMapping("/{noticeId}")
    public ApiResponse<UpdateNoticeResponse> updateNotice(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "수정할 공지 식별자", example = "1") @PathVariable Long noticeId,
            @Valid @RequestBody UpdateNoticeRequest request
    ) {
        /* 인증 회사·수정자·역할과 경로 식별자 및 검증된 본문을 NOTI-04 Command로 결합한다. */
        NoticeUpdateResult result = updateNoticeUseCase.updateNotice(request.toCommand(
                principal.getCompanyId(),
                noticeId,
                principal.getMemberId(),
                principal.getAuthority()
        ));

        /* 수정 직후 상세 화면에서 재조회 없이 사용할 수 있도록 최종 공지 전체를 반환한다. */
        return ApiResponse.success(
                "공지를 수정했습니다.",
                UpdateNoticeResponse.from(result)
        );
    }
}
