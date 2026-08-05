package com.module06.backend.capture.application.usecase;

import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.MeetingSummaryView;

/* ANLZ-03 · 요약 조회. */
public interface GetSummaryUseCase {

    MeetingSummaryView getSummary(long companyId, long meetingId);
}
