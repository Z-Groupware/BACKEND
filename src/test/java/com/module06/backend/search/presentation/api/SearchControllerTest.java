package com.module06.backend.search.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.search.application.query.SearchQuery;
import com.module06.backend.search.application.result.SearchResult;
import com.module06.backend.search.application.usecase.SearchUseCase;
import com.module06.backend.search.domain.model.SearchType;

/*
 * SR-1 Controller가 인증 principal과 Query Parameter를 유스케이스 Query로 변환하는지 검증한다.
 */
@DisplayName("SR-1 통합 검색 Controller")
class SearchControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/search는 공통 응답 봉투와 검색 결과를 반환한다")
    void returnsSearchResponseWithMockMvc() throws Exception {
        SearchQuery[] capturedQuery = new SearchQuery[1];
        SearchUseCase useCase = query -> {
            capturedQuery[0] = query;
            return new SearchResult(
                    "alpha",
                    new SearchResult.Counts(1, 1, 0, 0, 0),
                    List.of(new SearchResult.Item(
                            SearchType.MEETING,
                            91L,
                            "Alpha meeting",
                            "original overview",
                            new SearchResult.Project(7L, "PRD", "Product", "#123456"),
                            LocalDate.of(2026, 8, 9),
                            null,
                            100.0
                    ))
            );
        };

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SearchController(useCase))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(10L, 1L, "MEMBER", false, 100L),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        ));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", " alpha ")
                        .param("type", "MEETING")
                        .param("tags", "PRD")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("limit", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.message").value("검색 결과 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.query").value("alpha"))
                .andExpect(jsonPath("$.data.counts.meeting").value(1))
                .andExpect(jsonPath("$.data.results[0].type").value("MEETING"))
                .andExpect(jsonPath("$.data.results[0].snippet").value("original overview"))
                .andExpect(jsonPath("$.data.results[0].project.tag").value("PRD"));

        assertThat(capturedQuery[0].companyId()).isEqualTo(1L);
        assertThat(capturedQuery[0].requesterMemberId()).isEqualTo(10L);
        assertThat(capturedQuery[0].requesterTeamId()).isEqualTo(100L);
        assertThat(capturedQuery[0].requesterRole()).isEqualTo("MEMBER");
        assertThat(capturedQuery[0].admin()).isFalse();
        assertThat(capturedQuery[0].keyword()).isEqualTo(" alpha ");
        assertThat(capturedQuery[0].type()).isEqualTo(SearchType.MEETING);
        assertThat(capturedQuery[0].tags()).containsExactly("PRD");
        assertThat(capturedQuery[0].from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(capturedQuery[0].to()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(capturedQuery[0].limit()).isEqualTo(7);
    }
}
