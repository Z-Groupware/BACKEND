package com.module06.backend.cap.infrastructure.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.in.CreateSttBlockPort;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort.CreateSttBlockCommand;

/*
 * CAP-10 수동 업로드 STT 트리거가 capture의 CreateSttBlockPort에 올바른 값(blockSeq=0,
 * 구간 모름=0~0ms, cutReason="WHOLE_FILE")으로 위임하는지 검증한다.
 */
@DisplayName("CAP-10 수동 업로드 STT 실 어댑터")
class MeetingRecordingSttTranscribeAdapterTest {

    @Test
    @DisplayName("전체 파일을 blockSeq=0 · WHOLE_FILE 단일 블록으로 제출한다")
    void submitsWholeFileAsSingleBlock() {
        CreateSttBlockCommand[] captured = new CreateSttBlockCommand[1];
        CreateSttBlockPort createSttBlockPort = command -> captured[0] = command;
        MeetingRecordingSttTranscribeAdapter adapter =
                new MeetingRecordingSttTranscribeAdapter(createSttBlockPort);

        adapter.triggerWholeFileStt(500L, "recordings/org-1/meeting-500/recording.ogg");

        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].blockSeq()).isZero();
        assertThat(captured[0].startOffsetMs()).isZero();
        assertThat(captured[0].endOffsetMs()).isZero();
        assertThat(captured[0].cutReason()).isEqualTo("WHOLE_FILE");
        assertThat(captured[0].audioS3Key()).isEqualTo("recordings/org-1/meeting-500/recording.ogg");
    }
}
