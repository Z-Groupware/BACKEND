package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.RecordingPart;

import java.util.Collection;
import java.util.List;

// RecordingPart 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유
public interface RecordingPartRepository {

    /** 청크 하나를 저장한다 (append-only, 수정 없음) */
    RecordingPart save(RecordingPart recordingPart);

    /** 특정 세그먼트에서 업로드 기록(행)이 존재하는 청크 순번 목록 — missingSeqs 계산용(상태 무관). */
    List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq);

    /**
     * 특정 세그먼트에서 [fromSeq, toSeq] 범위의 청크 행 전체를 seq 순서대로 준다 — 실제 s3Key·
     * content_type이 필요한 10분 블록 오디오 조립(ffmpeg)용. missingSeqs 계산용인 위 메서드와
     * 달리 순번뿐 아니라 실제 행 전체가 필요할 때 쓴다.
     */
    List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq, int toSeq);

    /** 이 회의의 잔여 청크 조각을 모두 삭제한다(하드 삭제, CAP-15 — 오디오만 지운다). */
    void deleteByMeetingId(Long meetingId);

    /**
     * seq 목록에서 최댓값을 구한다(없으면 0) — "이 세그먼트에 실제로 올라온 마지막 순번"을 뜻한다.
     * RecordingGapChecker(CAP-05/MEET-08 seq 연속성 검증)와 RecordingAssemblyS3FfmpegAdapter
     * (실제 조립 대상 범위 계산) 양쪽이 "중간 세그먼트의 target은 최대 순번"이라는 같은 규칙을
     * 쓰므로, 이 한 줄짜리 계산을 여기 한 곳에 둬서 두 곳이 각자 손으로 다시 구현하지 않게 한다.
     */
    static int maxOf(Collection<Integer> seqs) {
        int max = 0;
        for (int seq : seqs) {
            if (seq > max) {
                max = seq;
            }
        }
        return max;
    }
}
