package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.repository.ProcessingCompletionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/* comment.
    ProcessingCompletionRepository 계약을 stt_block·analysis_layer read-model 두 개의 존재 쿼리로 구현.
    STT: DONE 아닌 블록이 있으면 미완료. 분석: PENDING/RUNNING/FAILED 계층이 있으면 미완료(SKIPPED/DONE은 OK).
*/
@Repository
public class ProcessingCompletionRepositoryAdapter implements ProcessingCompletionRepository {

    private static final String STT_DONE = "DONE";
    // capture ProcessingStatus 판정과 동일 — 이 세 상태 중 하나라도 있으면 분석 미완료.
    private static final List<String> ANALYSIS_UNFINISHED = List.of("PENDING", "RUNNING", "FAILED");

    private final SpringDataCapSttBlockReferenceRepository sttBlockRepository;
    private final SpringDataCapAnalysisLayerReferenceRepository analysisLayerRepository;

    public ProcessingCompletionRepositoryAdapter(
            SpringDataCapSttBlockReferenceRepository sttBlockRepository,
            SpringDataCapAnalysisLayerReferenceRepository analysisLayerRepository) {
        this.sttBlockRepository = sttBlockRepository;
        this.analysisLayerRepository = analysisLayerRepository;
    }

    @Override
    public boolean hasUnfinishedProcessing(Long meetingId) {
        return sttBlockRepository.existsByMeetingIdAndStatusNot(meetingId, STT_DONE)
                || analysisLayerRepository.existsByMeetingIdAndStatusIn(meetingId, ANALYSIS_UNFINISHED);
    }
}
