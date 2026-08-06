package com.module06.backend.cap.domain.repository;

/* comment.
    녹음 삭제(CAP-15)의 차단 판정용 읽기 계약 — STT 블록·분석 계층이 아직 안 끝났는지 본다.
    실제 데이터는 이태연(capture) 소유 stt_block/analysis_layer 테이블이지만, cap이 자기 read-model로 직접
    읽는다(meeting을 CapMeetingReferenceEntity로 읽는 것과 동일 패턴). 읽기만 하고 스키마는 안 건드린다.
*/
public interface ProcessingCompletionRepository {

    /**
     * 이 회의의 STT/분석이 아직 안 끝났는지 — 하나라도 미완료면 true(삭제 차단 근거).
     * "미완료" = DONE 아닌 stt_block이 있거나, PENDING/RUNNING/FAILED인 analysis_layer가 있음.
     */
    boolean hasUnfinishedProcessing(Long meetingId);
}
