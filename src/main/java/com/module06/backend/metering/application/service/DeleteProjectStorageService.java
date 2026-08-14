package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.usecase.DeleteProjectStorageUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteProjectStorageService implements DeleteProjectStorageUseCase {

    private final ProjectRepository projectRepository;
    private final MeetingStorageUsageRepository meetingStorageUsageRepository;
    private final MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;

    public DeleteProjectStorageService(ProjectRepository projectRepository,
                                       MeetingStorageUsageRepository meetingStorageUsageRepository,
                                       MeetingTextStorageUsageRepository meetingTextStorageUsageRepository) {
        this.projectRepository = projectRepository;
        this.meetingStorageUsageRepository = meetingStorageUsageRepository;
        this.meetingTextStorageUsageRepository = meetingTextStorageUsageRepository;
    }

    @Override
    @Transactional
    public void deleteByTag(Long companyId, String tag) {
        Project project = projectRepository.findByCompanyIdAndTag(companyId, tag)
                .orElseThrow(() -> new BusinessException(MeteringErrorCode.MT_STORAGE_PROJECT_NOT_FOUND));
        if (project.getStatus() != ProjectStatus.DONE) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_PROJECT_NOT_DONE);
        }

        meetingStorageUsageRepository.clearByCompanyIdAndProjectId(companyId, project.getId());
        meetingTextStorageUsageRepository.clearByCompanyIdAndProjectId(companyId, project.getId());
    }
}
