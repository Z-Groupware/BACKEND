package com.module06.backend.metering.domain.model;

/**
 * meeting_text_storage_usage에 독립적으로 리포트하는 세 producer. CAPTION은 cap
 * (SubmitCaptionsService), TRANSCRIPT·SUMMARY는 capture(SttResultPollingService,
 * MeetingSummaryPersistenceAdapter)가 각자 자기 컬럼만 갱신한다 — 자세한 이유는
 * meeting_text_storage_usage 마이그레이션(V4.9) 주석 참고.
 */
public enum TextStorageSource {
    CAPTION,
    TRANSCRIPT,
    SUMMARY
}
