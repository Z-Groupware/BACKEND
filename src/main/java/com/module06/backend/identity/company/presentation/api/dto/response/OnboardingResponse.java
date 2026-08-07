package com.module06.backend.identity.company.presentation.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.module06.backend.identity.company.application.dto.OnboardingResult;

@Schema(description = "온보딩 커밋 결과")
public record OnboardingResponse(
        LocalDateTime onboardedAt,
        int teamCount,
        int subTeamCount,
        int jobPositionCount,
        List<IssuedItem> issued,
        List<SkippedItem> skipped
) {
    public record IssuedItem(String email, String status) {
    }

    public record SkippedItem(String email, String reason) {
    }

    public static OnboardingResponse from(OnboardingResult result) {
        return new OnboardingResponse(
                result.onboardedAt(), result.teamCount(), result.subTeamCount(), result.jobPositionCount(),
                result.issued().stream().map(i -> new IssuedItem(i.email(), i.status())).toList(),
                result.skipped().stream().map(s -> new SkippedItem(s.email(), s.reason())).toList());
    }
}
