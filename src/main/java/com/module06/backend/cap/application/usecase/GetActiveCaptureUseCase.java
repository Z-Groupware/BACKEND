package com.module06.backend.cap.application.usecase;

import java.util.Optional;

// 컨트롤러가 부르는 "명찰" — 진행 중 캡처 조회(CAP-09)의 실제 구현체(CaptureQueryService)를 몰라도 되게 해준다.
public interface GetActiveCaptureUseCase {

    // 이 사람이 참석 중인 진행 중 캡처를 조회한다. 없으면 empty(응답 data:null).
    Optional<Result> getActiveCapture(Long memberId);

    /**
     * 조회 결과.
     *
     * @param captureSessionId 캡처 세션 ID. 회의 도메인(D) CAP-01이 발급하는 값인데 D가 아직 미구현이고
     *                         CAP-07 통보 경로도 recording_part.capture_session_id를 채우지 않으므로 현재는 항상 null.
     * @param canTakeover      현재 녹음자의 하트비트가 30초 이상 끊겼으면 true(이어받기 버튼 노출 근거).
     * @param elapsedMs        캡처 시작(첫 presign = capture_upload_state.created_at) 이후 경과 시간(ms).
     */
    record Result(
            Long meetingId,
            Long captureSessionId,
            int segmentSeq,
            int lastSeq,
            Long recorderPersonId,
            boolean canTakeover,
            long elapsedMs
    ) {
    }
}
