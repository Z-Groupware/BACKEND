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
import com.module06.backend.project.application.command.IssueAttachmentUploadUrlCommand;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.domain.model.ProjectAttachment;
import com.module06.backend.project.domain.repository.ProjectAttachmentRepository;
import com.module06.backend.project.domain.repository.ProjectRepository;
import com.module06.backend.project.exception.ProjectErrorCode;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private ProjectAttachmentRepository projectAttachmentRepository;

    @Mock
    private ProjectAttachmentStoragePort projectAttachmentStoragePort;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectAttachmentService service;

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
                PROJECT_ID, OTHER_COMPANY, "spec.pdf", "https://s3/spec.pdf", 1024L, STRANGER)))
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
        verify(projectAttachmentStoragePort).deleteObject("https://s3/spec.pdf");
    }

    private ProjectAttachment attachmentOf(Long projectId, Long uploadedBy) {
        return ProjectAttachment.reconstitute(
                ATTACHMENT_ID, projectId, "spec.pdf", "https://s3/spec.pdf", 1024L, uploadedBy, null, null);
    }
}
