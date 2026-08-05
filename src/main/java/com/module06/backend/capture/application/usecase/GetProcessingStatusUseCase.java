package com.module06.backend.capture.application.usecase;

import com.module06.backend.capture.application.result.ProcessingStatus;

/* CAP-06 · AI 처리 상태 조회. 사용자가 "어디까지 됐는지"를 보는 유일한 경로다. */
public interface GetProcessingStatusUseCase {

    ProcessingStatus getProcessingStatus(long companyId, long meetingId);
}
