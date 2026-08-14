package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.metering.application.usecase.DeleteProjectStorageUseCase;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import com.module06.backend.project.domain.model.ProjectStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 저장소 관리 화면(/manage/storage)의 HTTP 계약을 검증한다. 실제 메서드 보안을 활성화해
 * OWNER·ADMIN만 조회할 수 있는지와 집계 데이터가 없는 회사도 안정적인 빈 응답을 받는지 확인한다.
 */
@DisplayName("저장소 관리 화면 Controller")
@WebMvcTest(CompanyStorageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CompanyStorageControllerTest.MethodSecurityTestConfiguration.class)
class CompanyStorageControllerTest {

    private static final Long COMPANY_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStorageOverviewUseCase getStorageOverviewUseCase;

    @MockitoBean
    private DeleteProjectStorageUseCase deleteProjectStorageUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OWNER는 자기 회사의 저장소 현황을 조회한다")
    void ownerGetsStorageOverview() throws Exception {
        authenticateAs(new AuthPrincipal(1L, COMPANY_ID, "OWNER", false, null));
        when(getStorageOverviewUseCase.getOverview(COMPANY_ID)).thenReturn(storageOverview());

        mockMvc.perform(get("/api/companies/me/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.message").value("저장소 현황을 조회했습니다."))
                .andExpect(jsonPath("$.data.voiceGb").value(34.9))
                .andExpect(jsonPath("$.data.sttGb").value(6.8))
                .andExpect(jsonPath("$.data.projects[0].tag").value("eng"))
                .andExpect(jsonPath("$.data.projects[0].meetingCount").value(24));

        verify(getStorageOverviewUseCase).getOverview(COMPANY_ID);
    }

    @Test
    @DisplayName("ADMIN은 자기 회사의 저장소 현황을 조회한다")
    void adminGetsStorageOverview() throws Exception {
        authenticateAs(new AuthPrincipal(2L, COMPANY_ID, "LEADER", true, 3L));
        when(getStorageOverviewUseCase.getOverview(COMPANY_ID)).thenReturn(storageOverview());

        mockMvc.perform(get("/api/companies/me/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].tag").value("eng"));

        verify(getStorageOverviewUseCase).getOverview(COMPANY_ID);
    }

    @Test
    @DisplayName("LEADER는 저장소 관리 화면에 접근할 수 없다")
    void leaderIsForbidden() throws Exception {
        authenticateAs(new AuthPrincipal(3L, COMPANY_ID, "LEADER", false, 3L));

        mockMvc.perform(get("/api/companies/me/storage"))
                .andExpect(status().isForbidden())
                // CompanyStorageSecurityExceptionHandler가 범용 Z-002 대신 도메인 코드 MT-004를 쓴다
                // (CompanyStoragePlanService.requireOwnerOrAdmin과 통일) — 테스트가 이걸 못 따라갔었다.
                .andExpect(jsonPath("$.errorCode").value("MT-004"));

        verifyNoInteractions(getStorageOverviewUseCase);
    }

    @Test
    @DisplayName("MEMBER는 저장소 관리 화면에 접근할 수 없다")
    void memberIsForbidden() throws Exception {
        authenticateAs(new AuthPrincipal(4L, COMPANY_ID, "MEMBER", false, 3L));

        mockMvc.perform(get("/api/companies/me/storage"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MT-004"));

        verifyNoInteractions(getStorageOverviewUseCase);
    }

    @Test
    @DisplayName("플랜·집계 데이터가 없는 회사도 0 사용량과 빈 프로젝트 목록을 받는다")
    void companyWithoutGeneratedDataGetsStableEmptyResponse() throws Exception {
        authenticateAs(new AuthPrincipal(1L, COMPANY_ID, "OWNER", false, null));
        when(getStorageOverviewUseCase.getOverview(COMPANY_ID)).thenReturn(
                new StorageOverviewResult(new BigDecimal("0.0"), new BigDecimal("0.0"), List.of()));

        mockMvc.perform(get("/api/companies/me/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.voiceGb").value(0.0))
                .andExpect(jsonPath("$.data.sttGb").value(0.0))
                .andExpect(jsonPath("$.data.projects").isArray())
                .andExpect(jsonPath("$.data.projects").isEmpty());
    }

    @Test
    @DisplayName("프로젝트 저장 기록 삭제는 토큰 회사와 태그를 전달한다")
    void deleteProjectStorageDelegatesCompanyIdAndTag() throws Exception {
        authenticateAs(new AuthPrincipal(1L, COMPANY_ID, "OWNER", false, null));

        mockMvc.perform(delete("/api/companies/me/storage/projects/eng"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(deleteProjectStorageUseCase).deleteByTag(COMPANY_ID, "eng");
    }

    private StorageOverviewResult storageOverview() {
        return new StorageOverviewResult(new BigDecimal("34.9"), new BigDecimal("6.8"), List.of(
                new StorageOverviewResult.ProjectStorageItem("eng", "엔지니어링", 24L,
                        new BigDecimal("9.1"), new BigDecimal("1.4"),
                        LocalDate.of(2026, 8, 7), ProjectStatus.IN_PROGRESS)));
    }

    private void authenticateAs(AuthPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.authority()));
        if (principal.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
    }
}
