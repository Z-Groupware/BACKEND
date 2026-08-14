package com.module06.backend.project.application.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.command.ConfirmAttachmentCommand;
import com.module06.backend.project.application.command.DeleteAttachmentCommand;
import com.module06.backend.project.application.command.IssueAttachmentDownloadUrlCommand;
import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedDownloadUrl;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort.IssuedUploadUrl;
import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;
import com.module06.backend.project.domain.repository.ProjectRepository;
import com.module06.backend.project.exception.ProjectErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
    첨부파일은 회사 경계를 넘기 가장 쉬운 자리다 — 신원(회사·요청자)이 토큰에서 오도록 고친 뒤
    (2026-08-08), 서비스가 그 값을 실제로 검증하는지 여기서 고정한다.
*/
@ExtendWith(MockitoExtension.class)
class ProjectAttachmentServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long OTHER_COMPANY = 2L;
    private static final Long PROJECT_ID = 100L;
    private static final Long OTHER_PROJECT_ID = 200L;
    private static final Long ATTACHMENT_ID = 10L;
    private static final Long UPLOADER = 3L;
    private static final Long STRANGER = 4L;
    private static final String KEY = "project-attachments/company-%d/project-%d/uuid-spec.pdf".formatted(COMPANY, PROJECT_ID);

    @Mock
    private ProjectAttachmentRepository projectAttachmentRepository;

    @Mock
    private ProjectAttachmentStoragePort projectAttachmentStoragePort;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectAttachmentService service;

    @Test
    void 정상_요청이면_업로드_URL을_발급한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentStoragePort.issueUploadUrl(anyString(), eq(1024L)))
                .thenReturn(new IssuedUploadUrl("https://s3/upload", "https://s3/spec.pdf"));

        IssuedUploadUrl result = service.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(COMPANY, PROJECT_ID, "spec.pdf", 1024L));

        assertThat(result.uploadUrl()).isEqualTo("https://s3/upload");
        assertThat(result.fileUrl()).isEqualTo("https://s3/spec.pdf");

        // s3Key가 회사·프로젝트로 네임스페이싱됐는지 — 다른 회사/프로젝트 첨부와 섞이면 안 된다.
        // 원본 파일명은 확장자만 남기고 키에서 빠진다(아래 한글/공백 파일명 테스트가 이유를 검증).
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(projectAttachmentStoragePort).issueUploadUrl(keyCaptor.capture(), eq(1024L));
        assertThat(keyCaptor.getValue())
                .startsWith("project-attachments/company-%d/project-%d/".formatted(COMPANY, PROJECT_ID))
                .endsWith(".pdf")
                .doesNotContain("spec");
    }

    // 한글·공백이 든 파일명(녹음 앱 기본 파일명 등)이 presigned PUT 서명을 깨던 문제 — S3 키에
    // 원본 파일명을 더 이상 안 넣는다. 표시용 원본 파일명은 confirm()이 DB에 별도로 남긴다.
    @Test
    void 한글이나_공백이_든_파일명이어도_S3_키엔_원본_파일명이_들어가지_않는다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentStoragePort.issueUploadUrl(anyString(), eq(1024L)))
                .thenReturn(new IssuedUploadUrl("https://s3/upload", "https://s3/key"));

        service.issueUploadUrl(new IssueAttachmentUploadUrlCommand(
                COMPANY, PROJECT_ID, "음성 260814_124512.m4a", 1024L));

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(projectAttachmentStoragePort).issueUploadUrl(keyCaptor.capture(), eq(1024L));
        assertThat(keyCaptor.getValue())
                .startsWith("project-attachments/company-%d/project-%d/".formatted(COMPANY, PROJECT_ID))
                .endsWith(".m4a")
                .doesNotContain("음성")
                .doesNotContain(" ");
    }

    @Test
    void 파일명이_비어있으면_발급을_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(COMPANY, PROJECT_ID, " ", 1024L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(projectRepository, never()).existsActiveByCompanyIdAndId(any(), any());
    }

    @Test
    void 파일_크기가_0이하면_발급을_거부한다() {
        assertThatThrownBy(() -> service.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(COMPANY, PROJECT_ID, "spec.pdf", 0L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(projectRepository, never()).existsActiveByCompanyIdAndId(any(), any());
    }

    @Test
    void 파일명에_경로_조작이_있으면_발급을_거부한다() {
        // CodeRabbit(#313) 지적 — 검증 안 된 fileName이 s3Key에 그대로 들어가면 "report..pdf"
        // 같은 이름이 나중에 confirm의 requireOwnAttachmentKey를 자기 자신에 대해 오탐시킨다.
        assertThatThrownBy(() -> service.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(COMPANY, PROJECT_ID, "report..pdf", 1024L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(projectRepository, never()).existsActiveByCompanyIdAndId(any(), any());
    }

    @Test
    void 신규_파일이면_확정_시_새_첨부를_저장한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findByProjectIdAndFileUrl(PROJECT_ID, KEY))
                .thenReturn(Optional.empty());
        when(projectAttachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectAttachment result = service.confirm(new ConfirmAttachmentCommand(
                PROJECT_ID, COMPANY, "spec.pdf", KEY, 1024L, UPLOADER));

        assertThat(result.getFileName()).isEqualTo("spec.pdf");
        assertThat(result.getUploadedBy()).isEqualTo(UPLOADER);
        verify(projectAttachmentRepository).save(any());
    }

    @Test
    void 이미_확정된_파일이면_기존_첨부를_그대로_반환한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        ProjectAttachment existing = attachmentOf(PROJECT_ID, UPLOADER);
        when(projectAttachmentRepository.findByProjectIdAndFileUrl(PROJECT_ID, KEY))
                .thenReturn(Optional.of(existing));

        ProjectAttachment result = service.confirm(new ConfirmAttachmentCommand(
                PROJECT_ID, COMPANY, "spec.pdf", KEY, 1024L, UPLOADER));

        assertThat(result).isEqualTo(existing);
        verify(projectAttachmentRepository, never()).save(any());
    }

    @Test
    void 남의_회사_프로젝트로는_업로드_URL을_뽑지_못한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(OTHER_COMPANY, PROJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.issueUploadUrl(
                new IssueAttachmentUploadUrlCommand(OTHER_COMPANY, PROJECT_ID, "spec.pdf", 1024L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);

        verify(projectAttachmentStoragePort, never()).issueUploadUrl(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 남의_회사_프로젝트에는_첨부를_확정하지_못한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(OTHER_COMPANY, PROJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(new ConfirmAttachmentCommand(
                PROJECT_ID, OTHER_COMPANY, "spec.pdf", KEY, 1024L, STRANGER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);

        verify(projectAttachmentRepository, never()).save(any());
    }

    @Test
    void 남의_회사_프로젝트의_첨부는_삭제하지_못한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(OTHER_COMPANY, PROJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(
                new DeleteAttachmentCommand(OTHER_COMPANY, PROJECT_ID, ATTACHMENT_ID, UPLOADER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);

        verify(projectAttachmentRepository, never()).deleteById(any());
    }

    @Test
    void 경로의_프로젝트에_속하지_않은_첨부는_없는_것으로_답한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(OTHER_PROJECT_ID, UPLOADER)));

        /* 403 으로 구분해 주면 남의 첨부 id 를 넣어보는 것만으로 존재 여부가 새어 나간다. */
        assertThatThrownBy(() -> service.delete(
                new DeleteAttachmentCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID, UPLOADER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_NOT_FOUND);

        verify(projectAttachmentRepository, never()).deleteById(any());
    }

    @Test
    void 업로더가_아니면_삭제하지_못한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(PROJECT_ID, UPLOADER)));

        assertThatThrownBy(() -> service.delete(
                new DeleteAttachmentCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID, STRANGER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.NOT_ATTACHMENT_UPLOADER);

        verify(projectAttachmentRepository, never()).deleteById(any());
    }

    @Test
    void 내_회사_프로젝트의_내가_올린_첨부는_삭제된다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(PROJECT_ID, UPLOADER)));

        service.delete(new DeleteAttachmentCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID, UPLOADER));

        verify(projectAttachmentRepository).deleteById(ATTACHMENT_ID);
        verify(projectAttachmentStoragePort).deleteObject(KEY);
    }

    @Test
    void 삭제는_S3_객체를_지운_뒤에_DB에서_지운다() {
        // CodeRabbit(#313) 지적 — 순서가 반대(DB 먼저)면 S3 삭제 실패 시 고아 오브젝트가 영원히 남는다.
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(PROJECT_ID, UPLOADER)));

        service.delete(new DeleteAttachmentCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID, UPLOADER));

        var order = inOrder(projectAttachmentStoragePort, projectAttachmentRepository);
        order.verify(projectAttachmentStoragePort).deleteObject(KEY);
        order.verify(projectAttachmentRepository).deleteById(ATTACHMENT_ID);
    }

    @Test
    void 다른_프로젝트_네임스페이스의_키로는_확정하지_못한다() {
        // CodeRabbit(#313) 지적 — fileUrl은 클라이언트가 보내는 값이라 검증 없이 저장하면
        // 다른 프로젝트나 다른 도메인(cap의 recordings/...)의 실제 키를 자기 첨부로 확정한 뒤
        // 삭제 API로 그 객체를 지울 수 있다.
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        String foreignKey = "project-attachments/company-%d/project-%d/uuid-spec.pdf".formatted(COMPANY, OTHER_PROJECT_ID);

        assertThatThrownBy(() -> service.confirm(new ConfirmAttachmentCommand(
                PROJECT_ID, COMPANY, "spec.pdf", foreignKey, 1024L, UPLOADER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);

        verify(projectAttachmentRepository, never()).save(any());
    }

    @Test
    void 경로_조작이_포함된_키로는_확정하지_못한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        String traversalKey = KEY + "/../../../etc/passwd";

        assertThatThrownBy(() -> service.confirm(new ConfirmAttachmentCommand(
                PROJECT_ID, COMPANY, "spec.pdf", traversalKey, 1024L, UPLOADER)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);

        verify(projectAttachmentRepository, never()).save(any());
    }

    @Test
    void 정상_요청이면_다운로드_URL을_발급한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(PROJECT_ID, UPLOADER)));
        when(projectAttachmentStoragePort.issueDownloadUrl(KEY))
                .thenReturn(new IssuedDownloadUrl("https://s3/get", 300));

        IssuedDownloadUrl result = service.issueDownloadUrl(
                new IssueAttachmentDownloadUrlCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID));

        assertThat(result.downloadUrl()).isEqualTo("https://s3/get");
        assertThat(result.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    void 업로더가_아니어도_다운로드_URL을_발급받는다() {
        // 삭제와 달리 다운로드는 전 구성원 공개다 — 업로더 검증이 없어야 한다.
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(PROJECT_ID, UPLOADER)));
        when(projectAttachmentStoragePort.issueDownloadUrl(anyString()))
                .thenReturn(new IssuedDownloadUrl("https://s3/get", 300));

        IssuedDownloadUrl result = service.issueDownloadUrl(
                new IssueAttachmentDownloadUrlCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID));

        assertThat(result.downloadUrl()).isEqualTo("https://s3/get");
    }

    @Test
    void 남의_회사_프로젝트로는_다운로드_URL을_뽑지_못한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(OTHER_COMPANY, PROJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.issueDownloadUrl(
                new IssueAttachmentDownloadUrlCommand(OTHER_COMPANY, PROJECT_ID, ATTACHMENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_NOT_FOUND);

        verify(projectAttachmentStoragePort, never()).issueDownloadUrl(anyString());
    }

    @Test
    void 경로의_프로젝트에_속하지_않은_첨부는_다운로드_URL도_없는_것으로_답한다() {
        when(projectRepository.existsActiveByCompanyIdAndId(COMPANY, PROJECT_ID)).thenReturn(true);
        when(projectAttachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachmentOf(OTHER_PROJECT_ID, UPLOADER)));

        assertThatThrownBy(() -> service.issueDownloadUrl(
                new IssueAttachmentDownloadUrlCommand(COMPANY, PROJECT_ID, ATTACHMENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_NOT_FOUND);

        verify(projectAttachmentStoragePort, never()).issueDownloadUrl(anyString());
    }

    private ProjectAttachment attachmentOf(Long projectId, Long uploadedBy) {
        return ProjectAttachment.reconstitute(
                ATTACHMENT_ID, projectId, "spec.pdf", KEY, 1024L, uploadedBy, null, null);
    }
}
