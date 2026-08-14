package com.module06.backend.meetingroom.presentation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;

/*
 * ROOM-03~05 익명 요청과 ROOM-03~05 역할 위반이 유스케이스 호출 전에 차단되는지 검증한다.
 */
@DisplayName("ROOM-03~05 회의실 명령 보안")
@SpringBootTest
@AutoConfigureMockMvc
class MeetingRoomCommandSecurityTest {

    /* 실제 SecurityFilterChain을 통과하는 HTTP 요청을 실행한다. */
    @Autowired
    private MockMvc mockMvc;

    /* 익명 요청에서 애플리케이션 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private CreateMeetingRoomUseCase createMeetingRoomUseCase;

    /* ROOM-04 인증·역할 실패에서 수정 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private UpdateMeetingRoomUseCase updateMeetingRoomUseCase;

    /* ROOM-05 인증·역할 실패에서 비활성화 유스케이스가 호출되지 않는지 확인하는 대역이다. */
    @MockitoBean
    private DeactivateMeetingRoomUseCase deactivateMeetingRoomUseCase;

    /* Access Token 없는 등록 요청이 500이 아니라 공통 401 응답인지 검증한다. */
    @Test
    @DisplayName("익명 POST 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousCreateRequestBeforePrincipalResolution() throws Exception {
        /* Bean Validation을 통과할 수 있는 정상 본문을 인증 헤더 없이 전송한다. */
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "대회의실",
                                  "location": "박애관 421호"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 인증 실패 요청은 Controller와 등록 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(createMeetingRoomUseCase);
    }

    /* Access Token 없는 ROOM-04 요청이 기본 잠금으로 401인지 검증한다. */
    @Test
    @DisplayName("익명 PATCH 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousUpdateRequest() throws Exception {
        /* 정상 PATCH 본문을 인증 헤더 없이 전송한다. */
        mockMvc.perform(patch("/api/rooms/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "location": "본관 3층"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 기본 인증 단계에서 거절된 요청은 ROOM-04 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(updateMeetingRoomUseCase);
    }

    /*
     * ROOM-03은 @AuthenticationPrincipal(expression = "companyId")로 principal에서 직접
     * companyId를 뽑는다 — @WithMockUser 기본 principal(User)엔 companyId가 없어 SpEL 평가가
     * 터져 403이 아닌 500이 난다. 실제 JwtAuthenticationFilter가 만드는 것과 같은 AuthPrincipal
     * 기반 Authentication을 직접 주입해 실제 인증 형태를 재현한다.
     */
    private Authentication authenticationOf(String role) {
        return authenticationOf(role, false);
    }

    /*
     * JwtAuthenticationFilter는 isAdmin() 플래그가 있으면 base role 권한에 ROLE_ADMIN을
     * 더해 부여한다(어드민은 Authority 값이 아니라 member.is_admin 겸직 플래그이기 때문).
     * hasRole('ADMIN')은 이 ROLE_ADMIN으로 매칭되므로, 겸직 사용자를
     * 재현하려면 base role과 ROLE_ADMIN을 함께 부여해야 한다.
     */
    private Authentication authenticationOf(String role, boolean isAdmin) {
        AuthPrincipal principal = new AuthPrincipal(1L, 10L, role, isAdmin, null);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        if (isAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new TestingAuthenticationToken(principal, null, authorities);
    }

    /* 로그인했지만 관리 역할이 아닌 사용자가 ROOM-03을 호출할 수 없는지 검증한다. */
    @Test
    @DisplayName("MEMBER의 POST 요청을 403으로 거절한다")
    void rejectsMemberCreateRequest() throws Exception {
        /* MEMBER 인증을 가진 상태에서 정상 등록 본문을 전송한다. */
        mockMvc.perform(post("/api/rooms")
                        .with(authentication(authenticationOf("MEMBER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "대회의실",
                                  "location": "박애관 421호"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MR-004"));

        /* @PreAuthorize에서 거절된 요청은 ROOM-03 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(createMeetingRoomUseCase);
    }

    /* 로그인했지만 관리 역할이 아닌 사용자가 ROOM-03을 호출할 수 없는지 검증한다. */
    @Test
    @DisplayName("LEADER의 POST 요청을 403으로 거절한다")
    void rejectsLeaderCreateRequest() throws Exception {
        /* LEADER 인증을 가진 상태에서 정상 등록 본문을 전송한다. */
        mockMvc.perform(post("/api/rooms")
                        .with(authentication(authenticationOf("LEADER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "대회의실",
                                  "location": "박애관 421호"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MR-004"));

        /* @PreAuthorize에서 거절된 요청은 ROOM-03 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(createMeetingRoomUseCase);
    }

    /*
     * 회사 OWNER가 ADMIN 겸직 없이도 ROOM-03을 호출할 수 있는지 검증한다.
     * 등록은 수정·삭제(ROOM-04·05)와 같은 hasAnyRole('OWNER', 'ADMIN') 규칙이라,
     * base role OWNER면 겸직 플래그와 무관하게 유스케이스까지 통과해야 한다.
     */
    @Test
    @DisplayName("OWNER의 POST 요청을 유스케이스까지 통과시킨다")
    void allowsOwnerCreateRequest() throws Exception {
        /* 등록 유스케이스가 정상 결과를 반환하도록 스텁을 준비한다. */
        when(createMeetingRoomUseCase.createMeetingRoom(any()))
                .thenReturn(new MeetingRoomCreationResult(101L));

        /* ADMIN 겸직이 없는 순수 OWNER 인증으로 정상 등록 본문을 전송한다. */
        mockMvc.perform(post("/api/rooms")
                        .with(authentication(authenticationOf("OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "대회의실",
                                  "location": "박애관 421호"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    /*
     * 어드민 겸직 MEMBER가 ROOM-03을 호출할 수 있는지 검증한다. ADMIN은 Authority 값이
     * 아니라 member.is_admin 겸직 플래그이므로, hasAnyRole('OWNER', 'ADMIN')이 base role과
     * 무관하게 ROLE_ADMIN 겸직자를 통과시키는지가 핵심이다.
     */
    @Test
    @DisplayName("어드민 겸직 MEMBER의 POST 요청을 유스케이스까지 통과시킨다")
    void allowsAdminFlaggedMemberCreateRequest() throws Exception {
        /* 등록 유스케이스가 정상 결과를 반환하도록 스텁을 준비한다. */
        when(createMeetingRoomUseCase.createMeetingRoom(any()))
                .thenReturn(new MeetingRoomCreationResult(101L));

        /* 어드민 겸직 MEMBER 인증을 가진 상태에서 정상 등록 본문을 전송한다. */
        mockMvc.perform(post("/api/rooms")
                        .with(authentication(authenticationOf("MEMBER", true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "대회의실",
                                  "location": "박애관 421호"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    /* 로그인했지만 관리 역할이 아닌 사용자가 ROOM-04를 호출할 수 없는지 검증한다. */
    @Test
    @WithMockUser(roles = "MEMBER")
    @DisplayName("MEMBER의 PATCH 요청을 403으로 거절한다")
    void rejectsMemberUpdateRequest() throws Exception {
        /* MEMBER 인증을 가진 상태에서 정상 PATCH 본문을 전송한다. */
        mockMvc.perform(patch("/api/rooms/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "location": "본관 3층"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MR-004"));

        /* @PreAuthorize에서 거절된 요청은 ROOM-04 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(updateMeetingRoomUseCase);
    }

    /* Access Token 없는 ROOM-05 요청이 기본 잠금으로 401인지 검증한다. */
    @Test
    @DisplayName("익명 DELETE 요청을 AU-006과 401로 거절한다")
    void rejectsAnonymousDeactivateRequest() throws Exception {
        /* 인증 헤더 없이 2번 회의실 비활성화 요청을 전송한다. */
        mockMvc.perform(delete("/api/rooms/2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AU-006"));

        /* 기본 인증 단계에서 거절된 요청은 ROOM-05 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(deactivateMeetingRoomUseCase);
    }

    /* 로그인했지만 관리 역할이 아닌 사용자가 ROOM-05를 호출할 수 없는지 검증한다. */
    @Test
    @WithMockUser(roles = "LEADER")
    @DisplayName("LEADER의 DELETE 요청을 MR-004와 403으로 거절한다")
    void rejectsLeaderDeactivateRequest() throws Exception {
        /* LEADER 인증으로 2번 회의실 비활성화 요청을 전송한다. */
        mockMvc.perform(delete("/api/rooms/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MR-004"));

        /* @PreAuthorize에서 거절된 요청은 ROOM-05 유스케이스에 도달하면 안 된다. */
        verifyNoInteractions(deactivateMeetingRoomUseCase);
    }
}
