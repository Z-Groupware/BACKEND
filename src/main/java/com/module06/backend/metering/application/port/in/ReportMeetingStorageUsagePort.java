package com.module06.backend.metering.application.port.in;

import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;

/*
 * cap(과 향후 다른 캡처 소비 도메인)이 회의별 저장 용량 스냅샷을 보고하는 경계 — metering은 cap의
 * recording/recording_part 테이블을 직접 들여다보지 않고, 이 포트로 전달받은 값만 신뢰한다
 * (RecordTokenUsagePort와 동일한 도메인 경계 원칙).
 */
public interface ReportMeetingStorageUsagePort {

    void report(ReportMeetingStorageUsageCommand command);
}
