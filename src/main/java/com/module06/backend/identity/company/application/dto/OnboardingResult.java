package com.module06.backend.identity.company.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** §4-1 응답. */
public record OnboardingResult(
        LocalDateTime onboardedAt,
        int teamCount,
        int subTeamCount,
        int jobPositionCount,
        List<IssuedAccount> issued,
        List<SkippedInvite> skipped
) {
    public record IssuedAccount(String email, String status) {
    }

    public record SkippedInvite(String email, String reason) {
    }
}
