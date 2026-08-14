package com.module06.backend.metering.application.command;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.TextStorageSource;

/**
 * cap(자막)·capture(transcript·요약)가 회의 하나의 현재 자막·요약 저장 용량 스냅샷을 보고하는 요청.
 * usedBytes는 그 순간 **이 소스 하나가** 차지하는 총 바이트 — 델타가 아니라 절댓값이지만, 세 소스
 * (caption_chunk / transcript_chunk / meeting_summary+meeting_decision) 전체 총합이 아니라
 * source가 가리키는 한 소스만의 몫이다. 회사·회의 전체 총합은 metering이 세 소스를 더해서 낸다
 * (meeting_text_storage_usage 마이그레이션 V4.9 주석 참고).
 *
 * revision은 호출 시점 System.currentTimeMillis()를 쓴다(ReportMeetingStorageUsageCommand와 다른
 * 방식) — 음성은 recording.meeting_id가 UNIQUE라 회의당 생성 1번·삭제 1번뿐이라 고정 상수로
 * 충분했지만, 자막·transcript·요약은 같은 회의에 리포트가 여러 번(STT 블록마다·재요약마다) 들어와서
 * 고정값을 쓰면 두 번째 리포트부터 "이전 값보다 크지 않다"고 조용히 무시된다. 매 호출이 실제로
 * 더 큰 값이 되도록 벽시계를 쓴다.
 */
public record ReportMeetingTextStorageUsageCommand(
        Long companyId, Long projectId, Long meetingId, TextStorageSource source, long usedBytes, long revision) {

    public ReportMeetingTextStorageUsageCommand {
        if (companyId == null || projectId == null || meetingId == null || source == null || usedBytes < 0
                || revision < 0) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_RECORD_COMMAND_INVALID);
        }
    }
}
