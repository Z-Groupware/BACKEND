package com.module06.backend.metering.application.port.in;

import com.module06.backend.metering.application.command.ReportMeetingTextStorageUsageCommand;

/*
 * cap(caption_chunk)과 capture(transcript_chunk·meeting_summary)가 회의별 자막·요약 저장 용량
 * 스냅샷을 보고하는 경계 — ReportMeetingStorageUsagePort(음성)와 같은 원칙이지만 테이블을
 * 분리했다: 두 도메인이 같은 row를 나눠 갱신하면 락 경합·revision 이원화가 생기고, 음성 쪽처럼
 * "생성 1번·삭제 1번" 전제도 성립하지 않는다(자세한 이유는 meeting_text_storage_usage 마이그레이션
 * 주석 참고).
 */
public interface ReportMeetingTextStorageUsagePort {

    void report(ReportMeetingTextStorageUsageCommand command);
}
