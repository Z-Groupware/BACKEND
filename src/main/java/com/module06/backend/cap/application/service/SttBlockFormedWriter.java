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

    // 세그먼트 전환 직전 자투리(TAIL) 블록을 마무리한 뒤 카운터를 갱신한다. issuePartUploadUrls가
    // 이미 새 세그먼트로 저장을 마쳤을 수도 있는 시점에 비동기로 도착하므로, segmentSeq·
    // recorderPersonId는 건드리지 않고 blocksFormed·lastBlockEndOffsetMs만 갱신한다.
    @Transactional
    void recordSegmentTailBlockFormed(Long meetingId) {
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElseThrow();
        state.startNewSegmentBlockCounting();
        captureUploadStateRepository.save(state);
    }
}
