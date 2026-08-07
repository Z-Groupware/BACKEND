package com.module06.backend.identity.company.application.usecase;

import com.module06.backend.identity.company.application.command.OnboardCompanyCommand;
import com.module06.backend.identity.company.application.dto.OnboardingResult;

/** §4-1. */
public interface OnboardCompanyUseCase {

    OnboardingResult onboard(OnboardCompanyCommand command);
}
