package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 저장소 관리 화면(/manage/storage) API 보안 — 실제 SecurityFilterChain·@PreAuthorize·
 * CompanyStorageSecurityExceptionHandler를 통과하는 통합 테스트다(MeetingRoomCommandSecurityTest와
 * 동일 패턴). CompanyStorageControllerTest(단위 테스트, new CompanyStorageController(...) 직접
 * 호출)는 Spring이 프록시로 감싸지 않아 @PreAuthorize AOP가 전혀 안 걸린다 — 역할 게이트는
 * 여기서만 검증할 수 있다.
 *
 * LEADER/MEMBER가 403이 아니라 500(Z-003)을 받던 버그(GlobalExceptionHandler의
 * @ExceptionHandler(Exception.class)가 AuthorizationDeniedException까지 잡아버림)를
 * CompanyStorageSecurityExceptionHandler로 고쳤다 — 그 회귀를 여기서 고정한다.
 */
@DisplayName("저장소 관리 화면 API 보안")
@SpringBootTest
@AutoConfigureMockMvc
class CompanyStorageSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStorageOverviewUseCase getStorageOverviewUseCase;

    @Test
    @DisplayName("익명 요청은 401(AU-006)로 거절한다")
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/companies/me/storage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        verifyNoInteractions(getStorageOverviewUseCase);
    }

    @Test
    @DisplayName("OWNER는 저장소 현황을 조회할 수 있다")
    void ownerCanGetStorage() throws Exception {
        when(getStorageOverviewUseCase.getOverview(eq(10L)))
                .thenReturn(new StorageOverviewResult(new BigDecimal("34.9"), new BigDecimal("6.8"), List.of()));

        mockMvc.perform(get("/api/companies/me/storage").with(authentication(authenticationOf("OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voiceGb").value(34.9))
                .andExpect(jsonPath("$.data.sttGb").value(6.8));
    }

    @Test
    @DisplayName("ADMIN 겸직자는 저장소 현황을 조회할 수 있다")
    void adminCanGetStorage() throws Exception {
        when(getStorageOverviewUseCase.getOverview(eq(10L)))
                .thenReturn(new StorageOverviewResult(BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        mockMvc.perform(get("/api/companies/me/storage").with(authentication(authenticationOf("MEMBER", true))))
                .andExpect(status().isOk());
    }

    /*
     * 회귀 고정 — 고치기 전에는 이 요청이 500(Z-003)이었다. 역할만으로 거절되는 경로라
     * @PreAuthorize에서 걸려 유스케이스에 도달하면 안 된다(verifyNoInteractions).
     */
    @Test
    @DisplayName("LEADER의 GET 요청을 403(MT-004)으로 거절한다 — 이전엔 500이었다")
    void rejectsLeaderGetRequest() throws Exception {
        mockMvc.perform(get("/api/companies/me/storage").with(authentication(authenticationOf("LEADER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MT-004"));

        verifyNoInteractions(getStorageOverviewUseCase);
    }

    @Test
    @DisplayName("MEMBER의 GET 요청을 403(MT-004)으로 거절한다 — 이전엔 500이었다")
    void rejectsMemberGetRequest() throws Exception {
        mockMvc.perform(get("/api/companies/me/storage").with(authentication(authenticationOf("MEMBER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MT-004"));

        verifyNoInteractions(getStorageOverviewUseCase);
    }

    /* 플랜·집계 데이터가 없는 회사도 500/NaN/null 없이 0.0GB·빈 배열로 안정적으로 응답해야 한다. */
    @Test
    @DisplayName("데이터가 하나도 없는 회사도 200과 0.0GB·빈 목록을 받는다")
    void ownerGetsStableEmptyResponseForCompanyWithNoData() throws Exception {
        when(getStorageOverviewUseCase.getOverview(any()))
                .thenReturn(new StorageOverviewResult(BigDecimal.ZERO.setScale(1), BigDecimal.ZERO.setScale(1),
                        List.of()));

        mockMvc.perform(get("/api/companies/me/storage").with(authentication(authenticationOf("OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voiceGb").value(0.0))
                .andExpect(jsonPath("$.data.sttGb").value(0.0))
                .andExpect(jsonPath("$.data.projects").isArray())
                .andExpect(jsonPath("$.data.projects").isEmpty());
    }

    // MeetingRoomCommandSecurityTest.authenticationOf와 동일한 이유로 AuthPrincipal 기반
    // Authentication을 직접 만든다 — @WithMockUser 기본 principal(User)엔 companyId가 없어
    // @AuthenticationPrincipal(expression = "companyId")의 SpEL 평가가 403이 아니라 500으로 터진다.
    private Authentication authenticationOf(String role) {
        return authenticationOf(role, false);
    }

    private Authentication authenticationOf(String role, boolean isAdmin) {
        AuthPrincipal principal = new AuthPrincipal(1L, 10L, role, isAdmin, null);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        if (isAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new TestingAuthenticationToken(principal, null, authorities);
    }
}
