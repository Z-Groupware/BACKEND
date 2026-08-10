package com.module06.backend.cap.domain.repository;

import com.module06.backend.cap.domain.model.RecordingPart;

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
}
