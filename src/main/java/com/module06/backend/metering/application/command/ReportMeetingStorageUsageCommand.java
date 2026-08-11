package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

/**
 * cap이 회의 하나의 현재 저장 용량 스냅샷을 보고하는 요청. usedBytes는 그 순간 이 회의가 실제로
 * 차지하는 총 바이트(청크 누적합 또는 최종 조립본 크기) — 델타가 아니라 절댓값이다.
 *
 * revision은 호출자(cap)가 매기는 단조 증가 순번이다(예: lastSeq, 또는 조립 단계별 고정값).
 * 서버 수신 시각으로 순서를 판단하지 않는다 — 네트워크 지연·재시도로 report가 뒤바뀐 순서로
 * 도착하면, 서버 시각 기준으로는 최신 report를 오래된 값이 덮어써서 사용량이 과소 집계되고
 * cap의 한도 판정을 우회할 수 있다(CodeRabbit 지적). revision이 기존보다 크지 않으면 무시한다.
 */
public record ReportMeetingStorageUsageCommand(Long companyId, Long meetingId, long usedBytes, long revision) {

    public ReportMeetingStorageUsageCommand {
        if (companyId == null || meetingId == null || usedBytes < 0 || revision < 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_RECORD_COMMAND_INVALID);
        }
    }
}
