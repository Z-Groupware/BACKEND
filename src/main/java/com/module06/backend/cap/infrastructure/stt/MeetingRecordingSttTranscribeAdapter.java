package com.module06.backend.cap.infrastructure.stt;

import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort.CreateSttBlockCommand;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/* comment.
    MeetingRecordingSttPort의 실 어댑터 — capture의 인바운드 포트(CreateSttBlockPort)에 위임한다.
    수동 업로드본 전체를 "블록 0, 구간 모름(0~0ms), WHOLE_FILE"인 단일 블록으로 제출한다 —
    capture의 SttResultPollingService가 전사 완료 시 마지막 단어 끝 시각으로 실제 duration을 채운다
    (커밋 f42c4977 "수동 업로드 단일 블록 STT 를 받을 수 있게 한다" 참고 — cap 레인이 이 호출로
    갈아끼우기로 이미 합의된 지점).

    cap은 capture의 아웃바운드 포트(SttJobPort 등)를 직접 부르지 않는다 — 항상 port.in만 본다
    (CreateSttBlockPort 자체의 클래스 주석 참고).
*/
@Component
@Profile("prod")
public class MeetingRecordingSttTranscribeAdapter implements MeetingRecordingSttPort {

    private final CreateSttBlockPort createSttBlockPort;

    public MeetingRecordingSttTranscribeAdapter(CreateSttBlockPort createSttBlockPort) {
        this.createSttBlockPort = createSttBlockPort;
    }

    @Override
    public void triggerWholeFileStt(Long meetingId, String s3Key) {
        createSttBlockPort.createAndSubmitBlock(
                new CreateSttBlockCommand(meetingId, 0, 0, 0, "WHOLE_FILE", s3Key));
    }
}
