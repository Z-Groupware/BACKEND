package com.module06.backend.cap.application.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.module06.backend.cap.domain.repository.RecordingPartRepository;

/*
 * 세그먼트별 seq 연속성 검증 — 원래 RecordingAssemblyService(CAP-05, 클라이언트가 호출)에만 있던
 * 로직인데, MeetingCompletedAssemblyTrigger(MEET-08, 시스템이 자동 호출)도 조립 전에 같은 검증이
 * 필요해 공유 컴포넌트로 뺐다. 두 호출자 모두 "구멍이 있으면 조립을 시작하지 않는다"는 규칙은
 * 같고, 다만 구멍을 만났을 때 반응만 다르다(수동은 409 예외, 자동은 로그만 남기고 생략).
 */
@Component
class RecordingGapChecker {

    private final RecordingPartRepository recordingPartRepository;

    RecordingGapChecker(RecordingPartRepository recordingPartRepository) {
        this.recordingPartRepository = recordingPartRepository;
    }

    // 세그먼트 0..lastSegmentSeq를 훑어 각 세그먼트의 1..target이 빠짐 없이 있는지 본다.
    // 마지막 세그먼트의 target은 호출자가 넘긴 lastSeq, 중간 세그먼트는 그 세그먼트의 최대 순번
    // (중간 세그먼트는 이어받기로 넘어간 것이라 내부 연속성만 검증 가능).
    boolean hasGap(Long meetingId, int lastSegmentSeq, int lastSeq) {
        for (int segment = 0; segment <= lastSegmentSeq; segment++) {
            Set<Integer> present = new HashSet<>(recordingPartRepository.findSeqsInSegment(meetingId, segment));
            int target = (segment == lastSegmentSeq) ? lastSeq : maxOrZero(present);
            for (int seq = 1; seq <= target; seq++) {
                if (!present.contains(seq)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int maxOrZero(Set<Integer> seqs) {
        int max = 0;
        for (int seq : seqs) {
            if (seq > max) {
                max = seq;
            }
        }
        return max;
    }
}
