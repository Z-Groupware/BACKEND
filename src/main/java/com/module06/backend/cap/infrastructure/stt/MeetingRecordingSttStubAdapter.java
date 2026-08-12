package com.module06.backend.cap.infrastructure.stt;

import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/* comment.
    MeetingRecordingSttPort의 스텁 구현 — 로컬/테스트 전용(@Profile("!prod")). 운영은
    MeetingRecordingSttTranscribeAdapter(capture의 CreateSttBlockPort 위임)를 쓴다.
    RecordingAssemblyStubAdapter와 동일한 "스텁/실 어댑터" 짝 패턴.
*/
@Component
@Profile("!prod")
public class MeetingRecordingSttStubAdapter implements MeetingRecordingSttPort {

    private static final Logger log = LoggerFactory.getLogger(MeetingRecordingSttStubAdapter.class);

    @Override
    public void triggerWholeFileStt(Long meetingId, String s3Key) {
        log.info("수동 업로드 단일 블록 STT 트리거(stub) — meetingId={}, s3Key={}. "
                + "실제 stt_block 생성·Transcribe 호출·duration 복구는 후속 STT 인프라에서 수행.", meetingId, s3Key);
    }
}
