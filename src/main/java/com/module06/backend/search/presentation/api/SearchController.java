package com.module06.backend.search.presentation.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.search.application.query.RecentQueryCommand;
import com.module06.backend.search.application.query.RecentViewCommand;
import com.module06.backend.search.application.query.SearchQuery;
import com.module06.backend.search.application.result.SearchOverviewResult;
import com.module06.backend.search.application.result.SearchResult;
import com.module06.backend.search.application.usecase.SearchUseCase;
import com.module06.backend.search.domain.exception.SearchErrorCode;
import com.module06.backend.search.domain.model.SearchType;
import com.module06.backend.search.domain.repository.SearchQueryRepository.SearchScope;
import com.module06.backend.search.presentation.api.request.SaveRecentQueryRequest;
import com.module06.backend.search.presentation.api.request.SaveRecentViewRequest;
import com.module06.backend.search.presentation.api.response.SearchOverviewResponse;
import com.module06.backend.search.presentation.api.response.SearchResponse;

/*
 * SR-1 통합 검색 REST API 진입점이다.
 *
 * 인증 principal의 회사/구성원/팀/역할 정보만 유스케이스에 전달하고, 세부 접근 제어는 저장소 Query에서 강제한다.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchUseCase searchUseCase;

    public SearchController(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','LEADER','MEMBER')")
    @GetMapping
    public ApiResponse<SearchResponse> search(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "authority") String authority,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "isAdmin") boolean isAdmin,
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) List<String> tags,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(required = false) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        SearchResult result = searchUseCase.search(new SearchQuery(
                companyId,
                memberId,
                teamId,
                authority,
                isAdmin,
                q,
                parseType(type),
                tags,
                from,
                to,
                limit
        ));

        return ApiResponse.success(
                "검색 결과 조회에 성공했습니다.",
                SearchResponse.from(result)
        );
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','LEADER','MEMBER')")
    @GetMapping("/overview")
    public ApiResponse<SearchOverviewResponse> overview(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "teamId") Long teamId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "authority") String authority,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "isAdmin") boolean isAdmin
    ) {
        SearchOverviewResult result = searchUseCase.overview(new SearchScope(
                companyId,
                memberId,
                teamId,
                authority,
                isAdmin,
                List.of(),
                null,
                null
        ));

        return ApiResponse.success(
                "검색 개요 조회에 성공했습니다.",
                SearchOverviewResponse.from(result)
        );
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','LEADER','MEMBER')")
    @PostMapping("/recent-queries")
    public ApiResponse<Void> saveRecentQuery(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @RequestBody SaveRecentQueryRequest request
    ) {
        searchUseCase.saveRecentQuery(new RecentQueryCommand(
                companyId,
                memberId,
                request == null ? null : request.query()
        ));
        return ApiResponse.successWithoutData("최근 검색어 저장에 성공했습니다.");
    }

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','LEADER','MEMBER')")
    @PostMapping("/recent-views")
    public ApiResponse<Void> saveRecentView(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @RequestBody SaveRecentViewRequest request
    ) {
        searchUseCase.saveRecentView(new RecentViewCommand(
                companyId,
                memberId,
                parseRecentViewType(request == null ? null : request.type()),
                request == null ? null : request.id()
        ));
        return ApiResponse.successWithoutData("최근 본 항목 저장에 성공했습니다.");
    }

    private SearchType parseType(String type) {
        try {
            return SearchType.valueOf(type == null ? "ALL" : type.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER, exception);
        }
    }

    private SearchType parseRecentViewType(String type) {
        SearchType parsed = parseType(type);
        if (parsed == SearchType.ALL) {
            throw new BusinessException(SearchErrorCode.INVALID_RECENT_VIEW_TYPE);
        }
        return parsed;
    }
}
