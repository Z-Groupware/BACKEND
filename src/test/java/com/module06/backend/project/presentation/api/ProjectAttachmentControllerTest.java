package com.module06.backend.project.presentation.api;

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

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.project.application.command.ConfirmAttachmentCommand;
import com.module06.backend.project.application.command.DeleteAttachmentCommand;
import com.module06.backend.project.application.command.IssueAttachmentDownloadUrlCommand;
import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;
import com.module06.backend.project.application.usecase.ConfirmAttachmentUseCase;
import com.module06.backend.project.application.usecase.DeleteAttachmentUseCase;
import com.module06.backend.project.application.usecase.IssueAttachmentDownloadUrlUseCase;
import com.module06.backend.project.application.usecase.IssueAttachmentUploadUrlUseCase;
import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.exception.ProjectErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * ProjectControllerTest와 같은 이유 — 회사·요청자·업로더를 토큰에서만 꺼내는지 고정한다.
 * 헤더(X-Company-Id·X-Member-Id)로 받던 걸 2026-08-08에 바로잡은 구멍이라, 여기서도
 * 헤더를 보내도 무시되는지까지 확인한다.
 */
@DisplayName("ProjectAttachmentController")
@WebMvcTest(ProjectAttachmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueAttachmentUploadUrlUseCase issueAttachmentUploadUrlUseCase;

    @MockitoBean
    private IssueAttachmentDownloadUrlUseCase issueAttachmentDownloadUrlUseCase;

    @MockitoBean
    private ConfirmAttachmentUseCase confirmAttachmentUseCase;

    @MockitoBean
    private DeleteAttachmentUseCase deleteAttachmentUseCase;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("업로드 URL 발급은 토큰의 companyId를 쓴다 — 헤더를 보내도 무시된다")
    void issueUploadUrlTakesCompanyFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(issueAttachmentUploadUrlUseCase.issueUploadUrl(any()))
                .thenReturn(new IssuedUploadUrl("https://s3/upload", "https://s3/spec.pdf"));

        mockMvc.perform(post("/api/projects/100/attachments/upload-url")
                        .header("X-Company-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "spec.pdf", "fileSize": 1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3/upload"));

        ArgumentCaptor<IssueAttachmentUploadUrlCommand> captor =
                ArgumentCaptor.forClass(IssueAttachmentUploadUrlCommand.class);
        verify(issueAttachmentUploadUrlUseCase).issueUploadUrl(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().projectId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("확정은 토큰의 companyId·memberId를 requester/uploader로 쓴다")
    void confirmTakesScopeFromToken() throws Exception {
        authenticateAs(1L, 3L);
        when(confirmAttachmentUseCase.confirm(any())).thenReturn(attachment());

        mockMvc.perform(post("/api/projects/100/attachments/confirm")
                        .header("X-Member-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "spec.pdf", "fileUrl": "https://s3/spec.pdf", "fileSize": 1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("spec.pdf"));

        ArgumentCaptor<ConfirmAttachmentCommand> captor = ArgumentCaptor.forClass(ConfirmAttachmentCommand.class);
        verify(confirmAttachmentUseCase).confirm(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().uploadedBy())
                .as("헤더의 999가 아니라 토큰의 3이어야 한다")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("다운로드 URL 발급은 전 구성원 공개 — 업로더가 아니어도 응답을 받는다")
    void issueDownloadUrlIsOpenToAllMembers() throws Exception {
        authenticateAs(1L, 3L);
        when(issueAttachmentDownloadUrlUseCase.issueDownloadUrl(any()))
                .thenReturn(new IssuedDownloadUrl("https://s3/get", 300));

        mockMvc.perform(get("/api/projects/100/attachments/10/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://s3/get"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));

        ArgumentCaptor<IssueAttachmentDownloadUrlCommand> captor =
                ArgumentCaptor.forClass(IssueAttachmentDownloadUrlCommand.class);
        verify(issueAttachmentDownloadUrlUseCase).issueDownloadUrl(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().projectId()).isEqualTo(100L);
        assertThat(captor.getValue().attachmentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("존재하지 않는 첨부의 다운로드 URL 요청은 404다")
    void issueDownloadUrlPropagatesNotFoundException() throws Exception {
        authenticateAs(1L, 3L);
        doThrow(new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND))
                .when(issueAttachmentDownloadUrlUseCase).issueDownloadUrl(any());

        mockMvc.perform(get("/api/projects/100/attachments/10/download-url"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("삭제는 토큰의 memberId를 requesterId로 쓴다")
    void deleteTakesRequesterFromToken() throws Exception {
        authenticateAs(1L, 3L);

        mockMvc.perform(delete("/api/projects/100/attachments/10"))
                .andExpect(status().isOk());

        ArgumentCaptor<DeleteAttachmentCommand> captor = ArgumentCaptor.forClass(DeleteAttachmentCommand.class);
        verify(deleteAttachmentUseCase).delete(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(1L);
        assertThat(captor.getValue().projectId()).isEqualTo(100L);
        assertThat(captor.getValue().attachmentId()).isEqualTo(10L);
        assertThat(captor.getValue().requesterId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("업로더가 아니면 삭제 시 예외가 전파된다")
    void deletePropagatesNotUploaderException() throws Exception {
        authenticateAs(1L, 3L);
        doThrow(new BusinessException(ProjectErrorCode.NOT_ATTACHMENT_UPLOADER))
                .when(deleteAttachmentUseCase).delete(any());

        mockMvc.perform(delete("/api/projects/100/attachments/10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("빈 파일명은 400으로 거부된다")
    void issueUploadUrlRejectsBlankFileName() throws Exception {
        authenticateAs(1L, 3L);

        mockMvc.perform(post("/api/projects/100/attachments/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "", "fileSize": 1024}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void authenticateAs(Long companyId, Long memberId) {
        AuthPrincipal principal = new AuthPrincipal(memberId, companyId, "OWNER", false, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private ProjectAttachment attachment() {
        return ProjectAttachment.reconstitute(10L, 100L, "spec.pdf", "https://s3/spec.pdf", 1024L, 3L, null, null);
    }
}
