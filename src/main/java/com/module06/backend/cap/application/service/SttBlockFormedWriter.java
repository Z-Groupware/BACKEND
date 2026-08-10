package com.module06.backend.cap.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;

/*
 * SttBlockCutTrigger의 실제 DB 쓰기(블록 형성 카운터 갱신)만 담당하는 별도 협력자.
 * CompletePartUploadWriter와 같은 이유로 분리한다 — SttBlockCutTrigger 자신을 this.xxx()로
 * 불렀다면 @Transactional이 프록시를 못 거쳐 안 걸렸을 것이다.
 */
@Component
class SttBlockFormedWriter {

    private static final Logger log = LoggerFactory.getLogger(SttBlockFormedWriter.class);

    private final CaptureUploadStateRepository captureUploadStateRepository;

    SttBlockFormedWriter(CaptureUploadStateRepository captureUploadStateRepository) {
        this.captureUploadStateRepository = captureUploadStateRepository;
    }

    // 예약된 블록(reserveNextBlockSeq로 blocksFormed는 이미 전진해 있음)이 실제로 완성됐을 때
    // 끝 지점만 갱신한다. TAIL 블록은 이걸 부르지 않는다 — 새 세그먼트의 lastBlockEndOffsetMs는
    // assignOrVerifyRecorder가 세그먼트 전환 시점에 이미 0으로 리셋해뒀다.
    //
    // expectedSegmentSeq(예약 당시의 세그먼트)와 지금 세그먼트가 다르면 건너뛴다(CodeRabbit
    // 지적) — 그 사이 이어받기가 일어났다면 이 오프셋은 이미 지나간 세그먼트 것이라, 지금
    // 세그먼트의 lastBlockEndOffsetMs(0으로 갓 리셋됨)에 적용하면 상태가 오염된다.
    @Transactional
    void finalizeBlockOffset(Long meetingId, int expectedSegmentSeq, long cutOffsetMs) {
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElseThrow();
        if (!state.finalizeBlockOffsetIfSegmentMatches(expectedSegmentSeq, cutOffsetMs)) {
            log.info("블록 완료 도착 전에 세그먼트가 이미 바뀜 — 끝 지점 갱신을 건너뜀. "
                    + "meetingId={} expectedSegmentSeq={} 현재segmentSeq={}",
                    meetingId, expectedSegmentSeq, state.getSegmentSeq());
            return;
        }
        captureUploadStateRepository.save(state);
    }
}
