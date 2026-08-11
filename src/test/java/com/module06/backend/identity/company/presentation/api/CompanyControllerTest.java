package com.module06.backend.identity.company.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.OnboardCompanyCommand;
import com.module06.backend.identity.company.application.command.UpdateCompanyCommand;
import com.module06.backend.identity.company.application.dto.OnboardingResult;
import com.module06.backend.identity.company.application.usecase.GetCompanyProfileUseCase;
import com.module06.backend.identity.company.application.usecase.LookupCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.OnboardCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.RegisterCompanyUseCase;
import com.module06.backend.identity.company.application.usecase.UpdateCompanyProfileUseCase;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.member.domain.model.Authority;

/*
 * 프론트가 이 키 이름으로 보내고 받는다. 온보딩 요청은 필드 12개에 3단 중첩이고 예전 명세에서
 * 이름이 네 개(departments·children·positions·role) 바뀐 이력이 있어, 하나만 어긋나도 연동 당일
 * 통째로 막힌다 — 그래서 요청·응답 키를 여기서 고정한다(AuthControllerTest 와 같은 이유).
 *
 * @PreAuthorize 역할 차단은 이 슬라이스에서 평가되지 않는다(addFilters = false · 메서드 시큐리티
 * 미로드) — TeamControllerTest 주석과 같다. 여기서 보는 것은 JSON 계약이지 인가가 아니다.
 */
@DisplayName("CompanyController")
@WebMvcTest(CompanyController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LookupCompanyUseCase lookupCompanyUseCase;
    @MockitoBean
    private RegisterCompanyUseCase registerCompanyUseCase;
    @MockitoBean
    private OnboardCompanyUseCase onboardCompanyUseCase;
    @MockitoBean
    private GetCompanyProfileUseCase getCompanyProfileUseCase;
    @MockitoBean
    private UpdateCompanyProfileUseCase updateCompanyProfileUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /* ── 온보딩 커밋 ────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("온보딩 요청 키 12개를 그대로 커맨드로 넘긴다 — 3단 중첩이 전부 살아 있어야 한다")
    void passesOnboardingKeysThrough() throws Exception {
        authenticateAs(1L);
        when(onboardCompanyUseCase.onboard(any())).thenReturn(result());

        mockMvc.perform(post("/api/companies/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ONBOARDING_REQUEST))
                .andExpect(status().isOk());

        ArgumentCaptor<OnboardCompanyCommand> captor = ArgumentCaptor.forClass(OnboardCompanyCommand.class);
        verify(onboardCompanyUseCase).onboard(captor.capture());
        OnboardCompanyCommand command = captor.getValue();

        /* 부서 — teams[].tempId·name, subTeams[].tempId·name */
        assertThat(command.teams()).hasSize(2);
        OnboardCompanyCommand.TeamNode dev = command.teams().get(0);
        assertThat(dev.tempId()).isEqualTo("t1");
        assertThat(dev.name()).isEqualTo("개발팀");
        assertThat(dev.subTeams()).extracting(OnboardCompanyCommand.SubTeamNode::tempId)
                .containsExactly("s1", "s2");
        assertThat(dev.subTeams()).extracting(OnboardCompanyCommand.SubTeamNode::name)
                .containsExactly("프론트엔드", "백엔드");

        /* 역할 없는 부서는 빈 배열로 온다 — null 이 아니다. */
        assertThat(command.teams().get(1).subTeams()).isEmpty();

        /* 직급 — jobPositions[].tempId·name·defaultRole */
        assertThat(command.jobPositions()).extracting(OnboardCompanyCommand.JobPositionNode::tempId)
                .containsExactly("p1", "p2");
        assertThat(command.jobPositions().get(0).name()).isEqualTo("팀장");
        assertThat(command.jobPositions().get(0).defaultRole()).isEqualTo(Authority.LEADER);
        assertThat(command.jobPositions().get(1).defaultRole()).isEqualTo(Authority.MEMBER);

        /* 초대 — invites[].name·email·teamTempId·subTeamTempId·jobPositionTempId·isAdmin */
        OnboardCompanyCommand.InviteNode first = command.invites().get(0);
        assertThat(first.name()).isEqualTo("홍길동");
        assertThat(first.email()).isEqualTo("dev1@company.com");
        assertThat(first.teamTempId()).isEqualTo("t1");
        assertThat(first.subTeamTempId()).isEqualTo("s2");
        assertThat(first.jobPositionTempId()).isEqualTo("p2");
        assertThat(first.isAdmin()).isFalse();

        /* 역할을 안 고른 초대는 subTeamTempId 가 null 로 온다 — 팀장이 그런 경우다. */
        OnboardCompanyCommand.InviteNode leader = command.invites().get(1);
        assertThat(leader.subTeamTempId()).isNull();
        assertThat(leader.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("온보딩 응답 키를 고정한다 — 카운트 4개와 issued·skipped 목록")
    void returnsOnboardingCounts() throws Exception {
        authenticateAs(1L);
        when(onboardCompanyUseCase.onboard(any())).thenReturn(result());

        mockMvc.perform(post("/api/companies/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ONBOARDING_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardedAt").value("2026-08-08T10:22:00"))
                .andExpect(jsonPath("$.data.teamCount").value(2))
                .andExpect(jsonPath("$.data.subTeamCount").value(2))
                .andExpect(jsonPath("$.data.jobPositionCount").value(2))
                .andExpect(jsonPath("$.data.issued[0].email").value("dev1@company.com"))
                .andExpect(jsonPath("$.data.issued[0].status").value("SENT"))
                .andExpect(jsonPath("$.data.skipped[0].email").value("dup@company.com"))
                .andExpect(jsonPath("$.data.skipped[0].reason").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("온보딩 대상 회사는 바디가 아니라 토큰에서 온다 — 남의 회사를 온보딩할 수 없다")
    void onboardingTakesCompanyFromToken() throws Exception {
        authenticateAs(77L);
        when(onboardCompanyUseCase.onboard(any())).thenReturn(result());

        mockMvc.perform(post("/api/companies/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ONBOARDING_REQUEST))
                .andExpect(status().isOk());

        ArgumentCaptor<OnboardCompanyCommand> captor = ArgumentCaptor.forClass(OnboardCompanyCommand.class);
        verify(onboardCompanyUseCase).onboard(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("부서명이 비면 400 — 유스케이스를 부르지 않는다")
    void blankTeamNameIsRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/companies/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teams":[{"tempId":"t1","name":"","subTeams":[]}],
                                 "jobPositions":[{"tempId":"p1","name":"사원","defaultRole":"MEMBER"}],
                                 "invites":[]}
                                """))
                .andExpect(status().isBadRequest());

        verify(onboardCompanyUseCase, never()).onboard(any());
    }

    @Test
    @DisplayName("부서가 하나도 없으면 400 — 빈 조직으로 온보딩을 끝낼 수 없다")
    void emptyTeamsIsRejected() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/api/companies/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teams":[],
                                 "jobPositions":[{"tempId":"p1","name":"사원","defaultRole":"MEMBER"}],
                                 "invites":[]}
                                """))
                .andExpect(status().isBadRequest());

        verify(onboardCompanyUseCase, never()).onboard(any());
    }

    @Test
    @DisplayName("직급 defaultRole 이 OWNER 면 400·AU-021 — 온보딩으로는 오너를 못 만든다")
    void ownerDefaultRoleIsRejected() throws Exception {
        authenticateAs(1L);
        when(onboardCompanyUseCase.onboard(any()))
                .thenThrow(new BusinessException(AuthErrorCode.POSITION_ROLE_NOT_ASSIGNABLE));

        mockMvc.perform(post("/api/companies/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teams":[{"tempId":"t1","name":"개발팀","subTeams":[]}],
                                 "jobPositions":[{"tempId":"p1","name":"대표","defaultRole":"OWNER"}],
                                 "invites":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AU-021"));
    }

    /* ── 기업 기본 정보 ──────────────────────────────────────────────────────── */

    @Test
    @DisplayName("기업 정보 응답 키 9개 — 내부 이름이 아니라 businessNumber·phone 으로 나간다")
    void returnsCompanyProfileKeys() throws Exception {
        authenticateAs(1L);
        when(getCompanyProfileUseCase.getProfile(1L)).thenReturn(company());

        mockMvc.perform(get("/api/companies/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(1))
                .andExpect(jsonPath("$.data.code").value("NOVA-7K3D"))
                .andExpect(jsonPath("$.data.name").value("(주)테크스타트"))
                /* 내부 필드명은 registrationNo·mainPhone 이다 — JSON 으로는 이 이름이어야 한다. */
                .andExpect(jsonPath("$.data.businessNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.data.phone").value("02-1234-5678"))
                .andExpect(jsonPath("$.data.representativeName").value("김서준"))
                .andExpect(jsonPath("$.data.address").value("서울시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.onboardedAt").value("2026-08-08T10:22:00"))
                /* 내부 이름이 새어 나가면 프론트가 둘 다 읽으려 든다. */
                .andExpect(jsonPath("$.data.registrationNo").doesNotExist())
                .andExpect(jsonPath("$.data.mainPhone").doesNotExist());
    }

    @Test
    @DisplayName("PATCH 는 보낸 필드만 커맨드에 담는다 — 안 보낸 필드는 null 로 간다(부분 수정)")
    void patchSendsOnlyProvidedFields() throws Exception {
        authenticateAs(1L);
        when(updateCompanyProfileUseCase.updateProfile(any())).thenReturn(company());

        mockMvc.perform(patch("/api/companies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"서울시 강남구 테헤란로 123"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCompanyCommand> captor = ArgumentCaptor.forClass(UpdateCompanyCommand.class);
        verify(updateCompanyProfileUseCase).updateProfile(captor.capture());
        UpdateCompanyCommand command = captor.getValue();

        assertThat(command.companyId()).isEqualTo(1L);
        assertThat(command.address()).isEqualTo("서울시 강남구 테헤란로 123");
        assertThat(command.name()).isNull();
        assertThat(command.registrationNo()).isNull();
        assertThat(command.representativeName()).isNull();
        assertThat(command.mainPhone()).isNull();
    }

    @Test
    @DisplayName("businessNumber·phone 으로 보내면 내부 이름으로 옮겨 담는다")
    void patchMapsJsonNamesToInternalNames() throws Exception {
        authenticateAs(1L);
        when(updateCompanyProfileUseCase.updateProfile(any())).thenReturn(company());

        mockMvc.perform(patch("/api/companies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNumber":"999-88-77777","phone":"02-9999-8888"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCompanyCommand> captor = ArgumentCaptor.forClass(UpdateCompanyCommand.class);
        verify(updateCompanyProfileUseCase).updateProfile(captor.capture());

        assertThat(captor.getValue().registrationNo()).isEqualTo("999-88-77777");
        assertThat(captor.getValue().mainPhone()).isEqualTo("02-9999-8888");
    }

    @Test
    @DisplayName("code 를 보내도 무시된다 — 로그인 키라 수정 대상이 아니다")
    void codeInPatchBodyIsIgnored() throws Exception {
        authenticateAs(1L);
        when(updateCompanyProfileUseCase.updateProfile(any())).thenReturn(company());

        mockMvc.perform(patch("/api/companies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"HACK-0000","name":"(주)바뀐이름"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCompanyCommand> captor = ArgumentCaptor.forClass(UpdateCompanyCommand.class);
        verify(updateCompanyProfileUseCase).updateProfile(captor.capture());

        /* 커맨드에 code 를 담는 자리가 아예 없다 — 이름만 반영된다. */
        assertThat(captor.getValue().name()).isEqualTo("(주)바뀐이름");
    }

    /*
     * 화면이 빈 칸을 막고 있어도 서버가 막는 것과는 다르다. "" 가 들어오면 엔티티 병합이 null 검사만
     * 하므로 그대로 덮어써진다 — 기업명이 사라진다. 필드를 안 보내는 것(= 미변경)은 계속 허용해야
     * 하므로 @NotBlank 가 아니라 @Pattern 으로 "보냈으면 빈 값 금지"만 막는다.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("blankPatchBodies")
    @DisplayName("빈 값으로 보내면 400 — 유스케이스를 부르지 않는다")
    void blankPatchFieldIsRejected(String label, String body) throws Exception {
        authenticateAs(1L);

        mockMvc.perform(patch("/api/companies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(updateCompanyProfileUseCase, never()).updateProfile(any());
    }

    private static Stream<Arguments> blankPatchBodies() {
        return Stream.of(
                Arguments.of("name 빈 문자열", "{\"name\":\"\"}"),
                Arguments.of("name 공백만", "{\"name\":\"   \"}"),
                Arguments.of("businessNumber 빈 문자열", "{\"businessNumber\":\"\"}"),
                Arguments.of("representativeName 빈 문자열", "{\"representativeName\":\"\"}"),
                Arguments.of("address 빈 문자열", "{\"address\":\"\"}"),
                Arguments.of("address 탭만", "{\"address\":\"\\t\"}"),
                /* (?s) 를 붙여도 줄바꿈만 있는 값은 여전히 빈 값이다 — \S 가 하나도 없다. */
                Arguments.of("address 줄바꿈만", "{\"address\":\"\\n\"}"),
                Arguments.of("address 줄바꿈+공백만", "{\"address\":\" \\n \"}"),
                Arguments.of("phone 빈 문자열", "{\"phone\":\"\"}"));
    }

    @Test
    @DisplayName("줄바꿈이 든 값은 빈 값이 아니다 — 400 이 되면 안 된다")
    void newlineInsideValueIsNotBlank() throws Exception {
        authenticateAs(1L);
        when(updateCompanyProfileUseCase.updateProfile(any())).thenReturn(company());

        mockMvc.perform(patch("/api/companies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"서울시\\n강남구 테헤란로 123"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("필드를 아예 안 보내는 건 계속 허용된다 — 부분 수정 계약이 깨지지 않는다")
    void omittedFieldsStayAllowed() throws Exception {
        authenticateAs(1L);
        when(updateCompanyProfileUseCase.updateProfile(any())).thenReturn(company());

        mockMvc.perform(patch("/api/companies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"서울시 강남구 테헤란로 123"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCompanyCommand> captor = ArgumentCaptor.forClass(UpdateCompanyCommand.class);
        verify(updateCompanyProfileUseCase).updateProfile(captor.capture());

        assertThat(captor.getValue().address()).isEqualTo("서울시 강남구 테헤란로 123");
        assertThat(captor.getValue().name()).isNull();
    }

    /* ── 픽스처 ────────────────────────────────────────────────────────────── */

    /** 프론트에 그대로 넘기는 온보딩 요청 예시다 — 역할 있는 부서·없는 부서, 역할 지정·미지정을 모두 담았다. */
    private static final String ONBOARDING_REQUEST = """
            {
              "teams": [
                { "tempId": "t1", "name": "개발팀",
                  "subTeams": [ { "tempId": "s1", "name": "프론트엔드" },
                                { "tempId": "s2", "name": "백엔드" } ] },
                { "tempId": "t2", "name": "디자인팀", "subTeams": [] }
              ],
              "jobPositions": [
                { "tempId": "p1", "name": "팀장", "defaultRole": "LEADER" },
                { "tempId": "p2", "name": "사원", "defaultRole": "MEMBER" }
              ],
              "invites": [
                { "name": "홍길동", "email": "dev1@company.com",
                  "teamTempId": "t1", "subTeamTempId": "s2",
                  "jobPositionTempId": "p2", "isAdmin": false },
                { "name": "김서준", "email": "lead@company.com",
                  "teamTempId": "t1", "subTeamTempId": null,
                  "jobPositionTempId": "p1", "isAdmin": true }
              ]
            }
            """;

    private static OnboardingResult result() {
        return new OnboardingResult(
                LocalDateTime.of(2026, 8, 8, 10, 22, 0), 2, 2, 2,
                List.of(new OnboardingResult.IssuedAccount("dev1@company.com", "SENT")),
                List.of(new OnboardingResult.SkippedInvite("dup@company.com", "DUPLICATE_EMAIL")));
    }

    private static Company company() {
        return new Company(1L, "NOVA-7K3D", "(주)테크스타트", "123-45-67890",
                "김서준", "서울시 강남구 테헤란로 123", "02-1234-5678",
                LocalDateTime.of(2026, 8, 8, 10, 22, 0));
    }

    /** 필터를 끈 슬라이스라 컨텍스트를 직접 심는다 — AuthControllerTest 와 같은 방식. */
    private void authenticateAs(Long companyId) {
        AuthPrincipal principal = new AuthPrincipal(3L, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
