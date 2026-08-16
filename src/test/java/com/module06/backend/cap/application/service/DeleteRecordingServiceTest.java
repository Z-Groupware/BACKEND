package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.DeleteRecordingUseCase;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProcessingCompletionRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;

/*
 * CAP-15 녹음 삭제 서비스의 404·회사스코프(403)·STT/분석 완료 차단(409)·하드 삭제 규칙을 검증한다.
 * 회의는 회사 1 소속 고정(findCompanyId=1).
 */
@DisplayName("CAP-15 녹음 삭제 서비스")
class DeleteRecordingServiceTest {

    private static final String KEY = "recordings/org-1/meeting-500/recording.ogg";

    // 삭제 부수효과 기록(테스트별 새로).
    private final boolean[] storageDeleted = new boolean[1];
    private final boolean[] partsDeleted = new boolean[1];
    private final boolean[] recordingDeleted = new boolean[1];
    private final boolean[] captureStateDeleted = new boolean[1];
    private final List<String> deletedS3Keys = new ArrayList<>();
    private final ReportMeetingStorageUsageCommand[] reportedUsage = new ReportMeetingStorageUsageCommand[1];

    /* 회의(녹음)가 없으면 CAP-016으로 거절하는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-016으로 거절한다")
    void rejectsWhenMeetingMissing() {
        DeleteRecordingService service = service(Optional.empty(), Optional.empty(), false);

        assertErrorCode(() -> service.deleteRecording(500L, 1L, false), "CAP-016");
        assertNothingDeleted();
    }

    /* 다른 회사면 Z-002(ACCESS_DENIED)로 거절하는지 검증한다(owner/admin이라도). */
    @Test
    @DisplayName("다른 회사 녹음이면 Z-002로 거절한다")
    void rejectsWhenOtherCompany() {
        // 회의는 회사 1, 요청자 회사 2
        DeleteRecordingService service = service(Optional.of(1L), Optional.of(recording()), false);

        assertErrorCode(() -> service.deleteRecording(500L, 2L, false), "Z-002");
        assertNothingDeleted();
    }

    /* 녹음본이 없으면 CAP-016으로 거절하는지 검증한다. */
    @Test
    @DisplayName("녹음본이 없으면 CAP-016으로 거절한다")
    void rejectsWhenRecordingMissing() {
        DeleteRecordingService service = service(Optional.of(1L), Optional.empty(), false);

        assertErrorCode(() -> service.deleteRecording(500L, 1L, false), "CAP-016");
        assertNothingDeleted();
    }

    /* STT/분석 미완료 + confirm 없으면 CAP-017로 막고 아무것도 삭제하지 않는지 검증한다. */
    @Test
    @DisplayName("STT/분석 미완료 + confirm 없으면 CAP-017로 막는다")
    void rejectsWhenUnfinishedWithoutConfirm() {
        DeleteRecordingService service = service(Optional.of(1L), Optional.of(recording()), true);

        assertErrorCode(() -> service.deleteRecording(500L, 1L, false), "CAP-017");
        assertNothingDeleted();
    }

    /* STT/분석 미완료여도 confirm=true면 강행 삭제하는지 검증한다. */
    @Test
    @DisplayName("미완료여도 confirm=true면 강행 삭제한다")
    void deletesWhenUnfinishedButConfirmed() {
        DeleteRecordingService service = service(Optional.of(1L), Optional.of(recording()), true);

        DeleteRecordingUseCase.Result result = service.deleteRecording(500L, 1L, true);

        assertThat(result.freedBytes()).isEqualTo(15_000_000L);
        assertThat(result.deletedAt()).isNotNull();
        assertAllDeleted();
    }

    /* STT/분석 완료면 confirm 없이도 삭제하고 S3·part·recording을 모두 지우는지 검증한다. */
    @Test
    @DisplayName("완료 상태면 confirm 없이 삭제하고 오디오를 모두 지운다")
    void deletesWhenFinished() {
        DeleteRecordingService service = service(Optional.of(1L), Optional.of(recording()), false);

        DeleteRecordingUseCase.Result result = service.deleteRecording(500L, 1L, false);

        assertThat(result.freedBytes()).isEqualTo(15_000_000L);
        assertAllDeleted();
    }

    /*
     * 등록은 됐는데 조립의 parts 정리가 실패해 recording_part가 남아있는 경우(정상 조립 경로에서는
     * 보통 비어 있음), 그 잔여 청크의 S3 객체도 최종 파일과 함께 지우는지 검증한다 — DB 행만 지우고
     * S3 객체를 안 지우면 다시는 찾을 수 없는 고아 객체로 영영 남는다.
     */
    @Test
    @DisplayName("잔여 청크가 남아있으면 그 S3 객체도 최종 파일과 함께 지운다")
    void deletesLeftoverPartObjectsAlongsideFinalFile() {
        List<RecordingPart> leftover = List.of(
                RecordingPart.create(500L, 0, 1, "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm",
                        "audio/webm", 100L, 7L),
                RecordingPart.create(500L, 0, 2, "stt-temp/org-1/meeting-500/segments/0/parts/0002.webm",
                        "audio/webm", 100L, 7L));
        DeleteRecordingService service = service(Optional.of(1L), Optional.of(recording()), false, null, leftover);

        service.deleteRecording(500L, 1L, false);

        assertThat(deletedS3Keys).contains(
                "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm",
                "stt-temp/org-1/meeting-500/segments/0/parts/0002.webm",
                KEY);
        assertThat(partsDeleted[0]).isTrue();
    }

    /* recording.sttTriggered가 완료 판정 저장소로 그대로 전달되는지 검증한다(0건=완료 오판 방지, #11 코드리뷰 반영). */
    @Test
    @DisplayName("recording의 sttTriggered를 완료 판정에 그대로 넘긴다")
    void passesSttTriggeredToCompletionCheck() {
        boolean[] receivedSttTriggered = new boolean[1];
        Recording triggered = Recording.register(500L, "recording.ogg", KEY, 15_000_000L, true);
        DeleteRecordingService service = service(Optional.of(1L), Optional.of(triggered), false,
                (meetingId, sttTriggered) -> {
                    receivedSttTriggered[0] = sttTriggered;
                    return false;
                });

        service.deleteRecording(500L, 1L, false);

        assertThat(receivedSttTriggered[0]).isTrue();
    }

    private Recording recording() {
        return Recording.register(500L, "recording.ogg", KEY, 15_000_000L);
    }

    private void assertNothingDeleted() {
        assertThat(storageDeleted[0]).isFalse();
        assertThat(partsDeleted[0]).isFalse();
        assertThat(recordingDeleted[0]).isFalse();
        assertThat(captureStateDeleted[0]).isFalse();
    }

    private void assertAllDeleted() {
        assertThat(storageDeleted[0]).as("S3 삭제").isTrue();
        assertThat(partsDeleted[0]).as("recording_part 삭제").isTrue();
        assertThat(recordingDeleted[0]).as("recording 삭제").isTrue();
        assertThat(captureStateDeleted[0]).as("capture_upload_state 삭제").isTrue();
        // 삭제 후 저장 용량 미터링에 0바이트로 report됨.
        assertThat(reportedUsage[0]).isNotNull();
        assertThat(reportedUsage[0].usedBytes()).isZero();
        // 삭제 report의 revision은 항상 2(DELETE_REVISION, 생성=1보다 항상 큼) — 벽시계를 쓰지 않는다.
        assertThat(reportedUsage[0].revision()).isEqualTo(2L);
    }

    // 회의 companyId·녹음본·미완료여부를 지정해 서비스를 조립한다. 삭제 부수효과를 기록한다.
    private DeleteRecordingService service(Optional<Long> companyId, Optional<Recording> recording,
                                          boolean unfinished) {
        return service(companyId, recording, unfinished, null, List.of());
    }

    // 완료 판정 저장소를 직접 지정하고 싶을 때(예: sttTriggered 전달값 검증)를 위한 오버로드.
    private DeleteRecordingService service(Optional<Long> companyId, Optional<Recording> recording,
                                          boolean unfinished, ProcessingCompletionRepository completionOverride) {
        return service(companyId, recording, unfinished, completionOverride, List.of());
    }

    // 잔여 청크(recording_part) 목록까지 직접 지정하고 싶을 때(예: 잔여 청크 S3 삭제 검증)를 위한 오버로드.
    private DeleteRecordingService service(Optional<Long> companyId, Optional<Recording> recording,
                                          boolean unfinished, ProcessingCompletionRepository completionOverride,
                                          List<RecordingPart> leftoverParts) {
        storageDeleted[0] = false;
        partsDeleted[0] = false;
        recordingDeleted[0] = false;
        captureStateDeleted[0] = false;
        deletedS3Keys.clear();
        reportedUsage[0] = null;

        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return companyId.isPresent();
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                return true;
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return true;
            }

            @Override
            public Optional<Long> findCompanyId(Long meetingId) {
                return companyId;
            }

            @Override
            public int countAttendees(Long meetingId) {
                return 0;
            }

            @Override
            public Optional<Long> findProjectId(Long meetingId) {
                // 회의는 프로젝트 1 소속 고정 — companyId와 동일한 패턴.
                return companyId.isPresent() ? Optional.of(1L) : Optional.empty();
            }
        };
        RecordingRepository recordingRepo = new RecordingRepository() {
            @Override
            public Recording save(Recording r) {
                return r;
            }

            @Override
            public boolean existsByMeetingId(Long meetingId) {
                return recording.isPresent();
            }

            @Override
            public Optional<Recording> findByMeetingId(Long meetingId) {
                return recording;
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                recordingDeleted[0] = true;
            }
        };
        RecordingPartRepository partRepo = new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart p) {
                return p;
            }

            @Override
            public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                return List.of();
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                partsDeleted[0] = true;
            }

            @Override
            public List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq,
                                                                 int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public List<RecordingPart> findAllByMeetingId(Long meetingId) {
                return leftoverParts;
            }
        };
        ProcessingCompletionRepository completion = completionOverride != null
                ? completionOverride
                : (meetingId, sttTriggered) -> unfinished;
        CapObjectStoragePort storage = new CapObjectStoragePort() {
            @Override
            public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
                throw new AssertionError("삭제 경로에서 업로드 URL은 호출되면 안 됩니다.");
            }

            @Override
            public IssuedPlaybackUrl issuePlaybackUrl(String s3Key) {
                throw new AssertionError("삭제 경로에서 재생 URL은 호출되면 안 됩니다.");
            }

            @Override
            public void deleteRecording(String s3Key) {
                storageDeleted[0] = true;
                deletedS3Keys.add(s3Key);
            }

            @Override
            public boolean objectMatches(String s3Key, long expectedSizeBytes) {
                throw new AssertionError("삭제 경로에서 objectMatches는 호출되면 안 됩니다.");
            }
        };
        // 삭제 경로(회사 스코프만 씀)는 프로젝트 멤버 판정을 타지 않으므로 항상 false인 스텁으로 충분하다.
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> false;
        CapMeetingAccessGuard accessGuard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);
        ReportMeetingStorageUsagePort storagePort = command -> reportedUsage[0] = command;
        CaptureUploadStateRepository captureStateRepo = new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public CaptureUploadState save(CaptureUploadState state) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                captureStateDeleted[0] = true;
            }

            @Override
            public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed, int expectedSegmentSeq, long targetOffsetMs) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        return new DeleteRecordingService(meetingRef, accessGuard, recordingRepo, partRepo, completion, storage,
                storagePort, captureStateRepo);
    }

    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
