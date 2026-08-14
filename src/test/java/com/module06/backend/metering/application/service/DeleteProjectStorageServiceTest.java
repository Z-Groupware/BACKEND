package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteProjectStorageServiceTest {

    private static final Long COMPANY = 7L;
    private static final Long PROJECT = 9L;
    private static final String TAG = "eng";

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MeetingStorageUsageRepository meetingStorageUsageRepository;

    @Mock
    private MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;

    private DeleteProjectStorageService service;

    @BeforeEach
    void setUp() {
        service = new DeleteProjectStorageService(projectRepository, meetingStorageUsageRepository,
                meetingTextStorageUsageRepository);
    }

    @Test
    void missingTagThrowsNotFound() {
        when(projectRepository.findByCompanyIdAndTag(COMPANY, TAG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteByTag(COMPANY, TAG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_STORAGE_PROJECT_NOT_FOUND);

        verify(meetingStorageUsageRepository, never()).clearByCompanyIdAndProjectId(COMPANY, PROJECT);
        verify(meetingTextStorageUsageRepository, never()).clearByCompanyIdAndProjectId(COMPANY, PROJECT);
    }

    @Test
    void nonDoneProjectThrowsConflict() {
        when(projectRepository.findByCompanyIdAndTag(COMPANY, TAG))
                .thenReturn(Optional.of(project(ProjectStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.deleteByTag(COMPANY, TAG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_STORAGE_PROJECT_NOT_DONE);

        verify(meetingStorageUsageRepository, never()).clearByCompanyIdAndProjectId(COMPANY, PROJECT);
        verify(meetingTextStorageUsageRepository, never()).clearByCompanyIdAndProjectId(COMPANY, PROJECT);
    }

    @Test
    void doneProjectClearsVoiceAndTextLedgers() {
        when(projectRepository.findByCompanyIdAndTag(COMPANY, TAG))
                .thenReturn(Optional.of(project(ProjectStatus.DONE)));

        service.deleteByTag(COMPANY, TAG);

        verify(meetingStorageUsageRepository).clearByCompanyIdAndProjectId(COMPANY, PROJECT);
        verify(meetingTextStorageUsageRepository).clearByCompanyIdAndProjectId(COMPANY, PROJECT);
    }

    private Project project(ProjectStatus status) {
        return Project.reconstitute(PROJECT, COMPANY, TAG, "영어팀", "desc", "#6B7280", status,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1L, List.of(),
                null, LocalDateTime.now(), LocalDateTime.now());
    }
}
