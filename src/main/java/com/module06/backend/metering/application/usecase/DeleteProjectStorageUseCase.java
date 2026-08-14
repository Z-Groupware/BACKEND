package com.module06.backend.metering.application.usecase;

public interface DeleteProjectStorageUseCase {

    void deleteByTag(Long companyId, String tag);
}
