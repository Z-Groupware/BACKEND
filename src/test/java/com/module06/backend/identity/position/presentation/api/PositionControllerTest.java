package com.module06.backend.identity.position.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.application.command.CreatePositionCommand;
import com.module06.backend.identity.position.application.command.UpdatePositionCommand;
import com.module06.backend.identity.position.application.dto.PositionSummary;
import com.module06.backend.identity.position.application.usecase.CreatePositionUseCase;
import com.module06.backend.identity.position.application.usecase.DeletePositionUseCase;
import com.module06.backend.identity.position.application.usecase.GetPositionsUseCase;
import com.module06.backend.identity.position.application.usecase.UpdatePositionUseCase;

/*
 * TeamControllerTest 와 같은 방식 — companyId 가 토큰에서만 오는지를 고정한다.
 * @PreAuthorize 롤 차단은 이 슬라이스에서 평가되지 않는다(@EnableMethodSecurity 미로드) —
 * 레포 전역 컨벤션과 동일한 수준이라 이 태스크 범위에서 별도 통합 테스트를 추가하지 않는다.
 */
@DisplayName("PositionController")
@WebMvcTest(PositionController.class)
@AutoConfigureMockMvc(addFilters = false)
class PositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPositionsUseCase getPositionsUseCase;
    @MockitoBean
    private CreatePositionUseCase createPositionUseCase;
    @MockitoBean
    private UpdatePositionUseCase updatePositionUseCase;
    @MockitoBean
    private DeletePositionUseCase deletePositionUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("목록 조회는 토큰의 회사로만 조회한다")
    void listTakesCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(getPositionsUseCase.getPositions(1L)).thenReturn(List.of(
                new PositionSummary(1L, "사원", Authority.MEMBER, "설명", 0L)));

        mockMvc.perform(get("/api/job-positions"))
                .andExpect(status().isOk());

        verify(getPositionsUseCase).getPositions(1L);
    }

    @Test
    @DisplayName("헤더에 남의 회사 번호를 넣어도 토큰 값이 쓰인다")
    void listIgnoresSpoofedHeader() throws Exception {
        authenticateAs(1L);
        when(getPositionsUseCase.getPositions(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/job-positions").header("X-Company-Id", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(getPositionsUseCase).getPositions(captor.capture());
        assertThat(captor.getValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("생성은 토큰의 회사로 직급을 만든다")
    void createTakesCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(createPositionUseCase.create(any())).thenReturn(
                new PositionSummary(1L, "수석", Authority.LEADER, "팀 회의 개설", 0L));

        mockMvc.perform(post("/api/job-positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "수석", "authority": "LEADER", "description": "팀 회의 개설" }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreatePositionCommand> captor = ArgumentCaptor.forClass(CreatePositionCommand.class);
        verify(createPositionUseCase).create(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().name()).isEqualTo("수석");
        assertThat(captor.getValue().authority()).isEqualTo(Authority.LEADER);
    }

    @Test
    @DisplayName("직급명이 5자를 넘으면 400으로 거절한다")
    void createRejectsNameLongerThanFiveChars() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/job-positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "여섯글자직급명", "authority": "MEMBER", "description": "설명" }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPositionUseCase);
    }

    @Test
    @DisplayName("수정은 경로의 직급 id와 토큰의 회사로 요청한다")
    void updateTakesPositionIdFromPathAndCompanyFromToken() throws Exception {
        authenticateAs(1L);
        when(updatePositionUseCase.update(any())).thenReturn(
                new PositionSummary(10L, "대리", Authority.MEMBER, "새 설명", 0L));

        mockMvc.perform(patch("/api/job-positions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "대리", "authority": "MEMBER", "description": "새 설명" }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdatePositionCommand> captor = ArgumentCaptor.forClass(UpdatePositionCommand.class);
        verify(updatePositionUseCase).update(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().positionId()).isEqualTo(10L);
        assertThat(captor.getValue().name()).isEqualTo("대리");
    }

    @Test
    @DisplayName("삭제는 경로의 직급 id와 토큰의 회사로 요청한다")
    void deleteTakesPositionIdFromPathAndCompanyFromToken() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete("/api/job-positions/10"))
                .andExpect(status().isOk());

        verify(deletePositionUseCase).delete(1L, 10L);
    }

    private void authenticateAs(Long companyId) {
        AuthPrincipal principal = new AuthPrincipal(3L, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
