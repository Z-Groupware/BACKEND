package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

public record RecordTokenUsageCommand(
        Long companyId,
        Long teamId,
        Long meetingId,
        String jobId,
        int inputTokens,
        int outputTokens,
        String model
) {

    public RecordTokenUsageCommand {
        if (companyId == null || meetingId == null || isBlank(jobId) || isBlank(model)
                || inputTokens < 0 || outputTokens < 0) {
            throw new BusinessException(MeteringErrorCode.MT_RECORD_COMMAND_INVALID);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
