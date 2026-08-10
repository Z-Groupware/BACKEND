package com.module06.backend.cap.application.service;

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

    private final CaptureUploadStateRepository captureUploadStateRepository;

    SttBlockFormedWriter(CaptureUploadStateRepository captureUploadStateRepository) {
        this.captureUploadStateRepository = captureUploadStateRepository;
    }

    @Transactional
    void recordBlockFormed(Long meetingId, long cutOffsetMs) {
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElseThrow();
        state.recordBlockFormed(cutOffsetMs);
        captureUploadStateRepository.save(state);
    }
}
