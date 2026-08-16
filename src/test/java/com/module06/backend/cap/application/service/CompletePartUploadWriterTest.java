package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.CompletePartUploadCommand;
import com.module06.backend.cap.application.port.out.CaptureHeartbeatPort;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CapCaptureSessionReferenceRepository;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CaptureUploadService가 S3 HEAD 호출 전에 이미 세션 ACTIVE를 확인하지만, 그 네트워크 호출
 * 동안 세션이 PAUSED/ENDED로 바뀔 수 있어(CodeRabbit 지적) 실제 DB 쓰기 직전에 여기서 한 번 더
 * 확인한다. 이 파일은 그 재확인이 write() 자체에서 독립적으로 동작하는지 검증한다.
 */
@DisplayName("complete 실제 DB 쓰기(CompletePartUploadWriter)")
class CompletePartUploadWriterTest {

    private static final Long MEETING_ID = 500L;

    @Test
    @DisplayName("쓰기 시점에 세션이 ACTIVE가 아니면(PAUSED) 저장을 건너뛰고 CAP-020을 던진다")
    void rejectsWhenSessionPausedAtWriteTime() {
        CompletePartUploadWriter writer = writer(refusingRepos(), "PAUSED");
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);

        assertThatThrownBy(() -> writer.write(state, "key", "audio/webm", command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                .isEqualTo("CAP-020");
    }

    @Test
    @DisplayName("쓰기 시점에 세션이 없어졌으면(ENDED/세션없음) 저장을 건너뛰고 CAP-022를 던진다")
    void rejectsWhenSessionEndedAtWriteTime() {
        CompletePartUploadWriter writer = writer(refusingRepos(), null);
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);

        assertThatThrownBy(() -> writer.write(state, "key", "audio/webm", command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                .isEqualTo("CAP-022");
    }

    @Test
    @DisplayName("쓰기 시점에 세션이 여전히 ACTIVE면 정상적으로 저장한다")
    void writesWhenSessionStillActive() {
        RecordingPart[] saved = new RecordingPart[1];
        boolean[] stateSaved = new boolean[1];
        boolean[] heartbeatRefreshed = new boolean[1];

        RecordingPartRepository partRepo = new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                saved[0] = recordingPart;
                return recordingPart;
            }

            @Override
            public java.util.List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public java.util.List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq,
                                                                            int fromSeq, int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public java.util.List<RecordingPart> findAllByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        CaptureUploadStateRepository stateRepo = new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public CaptureUploadState save(CaptureUploadState value) {
                stateSaved[0] = true;
                return value;
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed, int expectedSegmentSeq, long targetOffsetMs, boolean completesSynchronously) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        CaptureHeartbeatPort heartbeat = new CaptureHeartbeatPort() {
            @Override
            public void refresh(Long meetingId) {
                heartbeatRefreshed[0] = true;
            }

            @Override
            public boolean isAlive(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        CaptureSessionActiveGuard guard = new CaptureSessionActiveGuard(sessionRef("ACTIVE"));
        CompletePartUploadWriter writer = new CompletePartUploadWriter(partRepo, stateRepo, heartbeat, guard);
        CaptureUploadState state = CaptureUploadState.startWithRecorder(MEETING_ID, 7L);

        writer.write(state, "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm", "audio/webm", command());

        assertThat(saved[0]).isNotNull();
        assertThat(stateSaved[0]).isTrue();
        assertThat(heartbeatRefreshed[0]).isTrue();
    }

    private CompletePartUploadCommand command() {
        return new CompletePartUploadCommand(MEETING_ID, 7L, 0, 1,
                "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm", 1_000L);
    }

    private CompletePartUploadWriter writer(RecordingPartRepository partRepo, String sessionStatus) {
        CaptureUploadStateRepository stateRepo = new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public CaptureUploadState save(CaptureUploadState value) {
                throw new AssertionError("세션이 ACTIVE가 아니므로 저장이 호출되면 안 됩니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed, int expectedSegmentSeq, long targetOffsetMs, boolean completesSynchronously) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        CaptureHeartbeatPort heartbeat = new CaptureHeartbeatPort() {
            @Override
            public void refresh(Long meetingId) {
                throw new AssertionError("세션이 ACTIVE가 아니므로 하트비트 갱신이 호출되면 안 됩니다.");
            }

            @Override
            public boolean isAlive(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        CaptureSessionActiveGuard guard = new CaptureSessionActiveGuard(sessionRef(sessionStatus));
        return new CompletePartUploadWriter(partRepo, stateRepo, heartbeat, guard);
    }

    // 세션이 ACTIVE가 아닐 때 저장 자체가 호출되면 안 되므로, save()가 호출되면 테스트가 실패하도록 던진다.
    private RecordingPartRepository refusingRepos() {
        return new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                throw new AssertionError("세션이 ACTIVE가 아니므로 저장이 호출되면 안 됩니다.");
            }

            @Override
            public java.util.List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public java.util.List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq,
                                                                            int fromSeq, int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public java.util.List<RecordingPart> findAllByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
    }

    private CapCaptureSessionReferenceRepository sessionRef(String status) {
        return new CapCaptureSessionReferenceRepository() {
            @Override
            public Optional<String> findStatus(Long meetingId) {
                return Optional.ofNullable(status);
            }

            @Override
            public Optional<Long> findSessionId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
    }
}
