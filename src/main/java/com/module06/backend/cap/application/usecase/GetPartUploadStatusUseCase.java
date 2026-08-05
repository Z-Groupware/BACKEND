package com.module06.backend.cap.application.usecase;

import java.util.List;

// 컨트롤러가 부르는 "명찰" — 청크 업로드 상태 조회(CAP-08)의 실제 구현체(CapturePartStatusService)를 몰라도 되게 한다.
public interface GetPartUploadStatusUseCase {

    // 현재 녹음자가 어디까지 올라갔는지 조회한다(재접속 재개용). 녹음자가 아니면 예외.
    Result getPartUploadStatus(Long meetingId, Long callerId);

    /**
     * 조회 결과.
     *
     * @param missingSeqs   현재 세그먼트에서 1..lastSeq 중 업로드 기록이 없는 순번(프론트가 그 구간만 재전송).
     * @param resumeFromSeq 재접속 시 이어서 업로드를 시작할 순번(= lastSeq + 1).
     * @param gapMs         녹음 자체가 없던 구간(ms). 진짜 값은 조립 시 duration 복구로만 알 수 있어 라이브 조회에선 항상 0.
     */
    record Result(
            int segmentSeq,
            int lastSeq,
            List<Integer> missingSeqs,
            int blocksFormed,
            int resumeFromSeq,
            long gapMs
    ) {
    }
}
