package com.module06.backend.cap.infrastructure.stt;

import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/* comment.
    MeetingRecordingSttPort의 스텁 구현 — 실제 stt_block 생성·AWS Transcribe 호출(이태연 STT 도메인)이
    배선되기 전까지 트리거를 로그로만 남긴다. RecordingAssemblyStubAdapter와 동일한 "실 어댑터 전 스텁" 패턴.
*/
@Component
public class MeetingRecordingSttStubAdapter implements MeetingRecordingSttPort {

    private static final Logger log = LoggerFactory.getLogger(MeetingRecordingSttStubAdapter.class);

    @Override
    public void triggerWholeFileStt(Long meetingId, String s3Key) {
        log.info("수동 업로드 단일 블록 STT 트리거(stub) — meetingId={}, s3Key={}. "
                + "실제 stt_block 생성·Transcribe 호출·duration 복구는 후속 STT 인프라에서 수행.", meetingId, s3Key);
    }
}
