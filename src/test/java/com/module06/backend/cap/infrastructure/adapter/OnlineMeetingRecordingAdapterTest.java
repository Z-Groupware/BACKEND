package com.module06.backend.cap.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.meeting.application.port.out.OnlineMeetingRecordingPort;

/* MEET-18 녹음 확정의 DB 커밋·S3 보상 삭제·커밋 후 용량 집계 순서를 검증한다. */
class OnlineMeetingRecordingAdapterTest {

    @BeforeEach
    void initializeSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("recording 저장 후 커밋된 경우에만 용량 집계와 STT 트리거를 시작한다")
    void startsDownstreamOnlyAfterCommit() {
        RecordingRepositoryStub repository = new RecordingRepositoryStub();
        StorageStub storage = new StorageStub();
        long[] reportedMeetingId = new long[1];
        SttStub sttStub = new SttStub();
        OnlineMeetingRecordingAdapter adapter = new OnlineMeetingRecordingAdapter(
                repository,
                storage,
                command -> reportedMeetingId[0] = command.meetingId(),
                sttStub
        );

        adapter.prepare(preparation());
        adapter.register(registration());

        assertThat(repository.saved).isNotNull();
        assertThat(reportedMeetingId[0]).isZero();
        assertThat(sttStub.triggeredMeetingId).isNull();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        assertThat(reportedMeetingId[0]).isEqualTo(91L);
        assertThat(sttStub.triggeredMeetingId).isEqualTo(91L);
        assertThat(storage.deletedKey).isNull();
    }

    @Test
    @DisplayName("회의 생성 트랜잭션이 롤백되면 먼저 업로드된 S3 객체를 보상 삭제한다")
    void deletesPendingObjectAfterRollback() {
        StorageStub storage = new StorageStub();
        OnlineMeetingRecordingAdapter adapter = new OnlineMeetingRecordingAdapter(
                new RecordingRepositoryStub(), storage, command -> { }, new SttStub());

        adapter.prepare(preparation());
        adapter.register(registration());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(storage.deletedKey).isEqualTo(registration().s3Key());
    }

    private OnlineMeetingRecordingPort.Registration registration() {
        return new OnlineMeetingRecordingPort.Registration(
                10L,
                3L,
                12L,
                91L,
                "recordings/org-10/member-3/online-pending/upload-id/meeting.mp3",
                "meeting.mp3",
                "audio/mpeg",
                1_024L
        );
    }

    private OnlineMeetingRecordingPort.Preparation preparation() {
        OnlineMeetingRecordingPort.Registration registration = registration();
        return new OnlineMeetingRecordingPort.Preparation(
                registration.companyId(),
                registration.hostMemberId(),
                registration.s3Key(),
                registration.fileName(),
                registration.contentType(),
                registration.sizeBytes()
        );
    }

    private static final class RecordingRepositoryStub implements RecordingRepository {
        private Recording saved;

        @Override
        public Recording save(Recording recording) {
            saved = recording;
            return recording;
        }

        @Override
        public boolean existsByMeetingId(Long meetingId) {
            return false;
        }

        @Override
        public Optional<Recording> findByMeetingId(Long meetingId) {
            return Optional.empty();
        }

        @Override
        public void deleteByMeetingId(Long meetingId) {
        }
    }

    private static final class SttStub implements MeetingRecordingSttPort {
        private Long triggeredMeetingId;

        @Override
        public void triggerWholeFileStt(Long meetingId, String s3Key) {
            triggeredMeetingId = meetingId;
        }
    }

    private static final class StorageStub implements CapObjectStoragePort {
        private String deletedKey;

        @Override
        public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
            throw new AssertionError("녹음 확정 단계에서 업로드 URL을 다시 발급하면 안 됩니다.");
        }

        @Override
        public IssuedPlaybackUrl issuePlaybackUrl(String s3Key) {
            throw new AssertionError("녹음 확정 단계에서 재생 URL을 발급하면 안 됩니다.");
        }

        @Override
        public void deleteRecording(String s3Key) {
            deletedKey = s3Key;
        }

        @Override
        public boolean objectMatches(String s3Key, long expectedSizeBytes) {
            return true;
        }
    }
}
