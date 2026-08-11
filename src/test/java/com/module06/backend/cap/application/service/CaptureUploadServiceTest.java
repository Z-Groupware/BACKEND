package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.CompletePartUploadCommand;
import com.module06.backend.cap.application.command.IssuePartUploadUrlsCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.port.out.CaptureHeartbeatPort;
import com.module06.backend.cap.application.port.out.SttBlockAudioAssemblyPort;
import com.module06.backend.cap.application.port.out.SttBlockCutDetectionPort;
import com.module06.backend.cap.application.usecase.IssuePartUploadUrlsUseCase;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CapCaptureSessionReferenceRepository;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;
import com.module06.backend.metering.application.port.in.StorageQuotaPort;
import com.module06.backend.metering.application.result.StorageQuotaStatusResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

/*
 * presign(#4)+complete(#7) 실제 로직(CaptureUploadService)의 회의 존재·참석자 검증·녹음자
 * 배정/검증(하트비트 기반 이어받기 포함)·s3Key/segmentSeq 위조 차단 규칙을 검증하는 단위 테스트다.
 * 지금까지 이 서비스는 컨트롤러 계약 테스트(CaptureUploadControllerTest, UseCase 인터페이스를
 * 페이크)로만 간접 커버됐고 서비스 자체의 단위 테스트가 없었다 — 이 파일이 그 공백을 메운다.
 */
@DisplayName("presign/complete 캡처 업로드 서비스")
class CaptureUploadServiceTest {

    private static final long MEETING_ID = 500L;
    private static final long COMPANY_ID = 1L;
    private static final long CALLER_ID = 7L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-11T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    // presign이 발급한 URL 개수·complete가 저장한 청크·하트비트 refresh 호출 여부를 기록한다.
    private final List<RecordingPart> savedParts = new ArrayList<>();
    private final boolean[] heartbeatRefreshed = new boolean[1];
    private final List<ReportMeetingStorageUsageCommand> reportedUsages = new ArrayList<>();

    // ── presign(issuePartUploadUrls) ──

    /* 회의가 없으면 CAP-002로 거절하는지 검증한다. */
    @Test
    @DisplayName("presign: 회의가 없으면 CAP-002로 거절한다")
    void issue_rejectsWhenMeetingMissing() {
        CaptureUploadService service = service(Optional.empty(), true, null, true, true);

        assertErrorCode(() -> service.issuePartUploadUrls(issueCmd(2)), "CAP-002");
    }

    /* 참석자가 아니면 CAP-010으로 거절하는지 검증한다(비참석자가 회의ID만으로 녹음자로 배정되는 것 차단). */
    @Test
    @DisplayName("presign: 참석자가 아니면 CAP-010으로 거절한다")
    void issue_rejectsNonAttendee() {
        CaptureUploadService service = service(Optional.of(COMPANY_ID), false, null, true, true);

        assertErrorCode(() -> service.issuePartUploadUrls(issueCmd(2)), "CAP-010");
    }

    /* 첫 presign(상태 없음)이면 호출자가 녹음자로 배정되고, 요청한 개수만큼 세그먼트 0의 URL이 발급되는지 검증한다. */
    @Test
    @DisplayName("presign: 첫 호출이면 녹음자로 배정되고 count개 URL을 발급한다")
    void issue_firstCallAssignsRecorderAndIssuesUrls() {
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, null, true, true);

        IssuePartUploadUrlsUseCase.Result result = service.issuePartUploadUrls(issueCmd(3));

        assertThat(result.segmentSeq()).isZero();
        assertThat(result.parts()).hasSize(3);
        assertThat(result.parts()).extracting(IssuePartUploadUrlsUseCase.Part::seq)
                .containsExactly(1, 2, 3);
        assertThat(result.parts().get(0).presignedUrl()).contains("segments/0/parts/0001");
        assertThat(heartbeatRefreshed[0]).isTrue();
    }

    /* 이미 녹음자가 있고 같은 사람이 재호출하면(정상 배치 재요청) 세그먼트가 그대로 유지되는지 검증한다. */
    @Test
    @DisplayName("presign: 현재 녹음자 본인의 재호출은 세그먼트를 유지한다")
    void issue_sameRecorderKeepsSegment() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true);

        IssuePartUploadUrlsUseCase.Result result = service.issuePartUploadUrls(issueCmd(1));

        assertThat(result.segmentSeq()).isZero();
    }

    /* 다른 사람이 녹음자인데 하트비트가 살아있으면(30초 이내) 이어받기가 막혀 CAP-004로 거절되는지 검증한다. */
    @Test
    @DisplayName("presign: 녹음자가 살아있으면 다른 사람의 이어받기를 CAP-004로 거절한다")
    void issue_rejectsTakeoverWhenRecorderAlive() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, 99L);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true);

        assertErrorCode(() -> service.issuePartUploadUrls(issueCmd(1)), "CAP-004");
    }

    /* 녹음자의 하트비트가 끊겼으면(30초 초과) 다른 참석자가 이어받아 세그먼트가 올라가는지 검증한다. */
    @Test
    @DisplayName("presign: 녹음자가 끊겼으면 다른 참석자가 이어받고 세그먼트가 올라간다")
    void issue_allowsTakeoverWhenRecorderDead() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, 99L);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, false, true);

        IssuePartUploadUrlsUseCase.Result result = service.issuePartUploadUrls(issueCmd(1));

        assertThat(result.segmentSeq()).isEqualTo(1);
    }

    /* 새 회의(상태 없음)인데 회사 저장 용량이 이미 한도를 넘었으면 CAP-003으로 거절하는지 검증한다. */
    @Test
    @DisplayName("presign: 새 회의인데 저장 용량 한도를 넘었으면 CAP-003으로 거절한다")
    void issue_rejectsNewRecordingWhenOverQuota() {
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, null, true, true, false, true);

        assertErrorCode(() -> service.issuePartUploadUrls(issueCmd(1)), "CAP-003");
    }

    /*
     * 이미 진행 중인 회의(상태 있음)는 그 사이 회사가 한도를 넘겨도 끊지 않는지 검증한다 — presign은
     * 15초마다 반복 호출되는데, 매번 확인하면 진행 중인 녹음이 중간에 끊길 수 있다.
     */
    @Test
    @DisplayName("presign: 이미 진행 중인 회의는 한도를 넘어도 막지 않는다")
    void issue_allowsExistingRecordingEvenWhenOverQuota() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true, false, true);

        IssuePartUploadUrlsUseCase.Result result = service.issuePartUploadUrls(issueCmd(1));

        assertThat(result.parts()).hasSize(1);
    }

    /*
     * 회사가 아직 저장 용량 한도를 설정하지 않았으면(옵트인 기능이라 대부분 그렇다)
     * StorageQuotaPort.getStatus가 MT_STORAGE_PLAN_NOT_FOUND를 던진다 — 이 조회 실패가
     * 새 회의 녹음 시작 자체를 막으면 안 된다(fail-open, 실제 발견된 회귀 버그).
     */
    @Test
    @DisplayName("presign: 저장 용량 한도 조회가 실패해도(플랜 미설정 등) 녹음을 막지 않는다")
    void issue_allowsNewRecordingWhenQuotaLookupFails() {
        CaptureUploadService service =
                service(Optional.of(COMPANY_ID), true, null, true, true, false, false, true);

        IssuePartUploadUrlsUseCase.Result result = service.issuePartUploadUrls(issueCmd(1));

        assertThat(result.parts()).hasSize(1);
    }

    // ── complete(completePartUpload) ──

    /* 회의가 없으면 CAP-002로 거절하는지 검증한다. */
    @Test
    @DisplayName("complete: 회의가 없으면 CAP-002로 거절한다")
    void complete_rejectsWhenMeetingMissing() {
        CaptureUploadService service = service(Optional.empty(), true, null, true, true);

        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm"))),
                "CAP-002");
        assertThat(savedParts).isEmpty();
    }

    /* 참석자가 아니면 CAP-010으로 거절하는지 검증한다. */
    @Test
    @DisplayName("complete: 참석자가 아니면 CAP-010으로 거절한다")
    void complete_rejectsNonAttendee() {
        CaptureUploadService service = service(Optional.of(COMPANY_ID), false, null, true, true);

        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm"))),
                "CAP-010");
    }

    /* presign이 한 번도 호출된 적 없으면(상태 없음) CAP-004로 거절되는지 검증한다. */
    @Test
    @DisplayName("complete: presign 이력이 없으면 CAP-004로 거절한다")
    void complete_rejectsWhenStateMissing() {
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, null, true, true);

        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm"))),
                "CAP-004");
        assertThat(savedParts).isEmpty();
    }

    /* 현재 녹음자가 아닌 사람이 완료 통보하면 CAP-004로 거절되는지 검증한다. */
    @Test
    @DisplayName("complete: 현재 녹음자가 아니면 CAP-004로 거절한다")
    void complete_rejectsNonRecorder() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, 99L);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true);

        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm"))),
                "CAP-004");
        assertThat(savedParts).isEmpty();
    }

    /* 본문 segmentSeq가 서버가 아는 현재 세그먼트와 다르면 CAP-009로 거절되는지 검증한다(위조 세그먼트 방지). */
    @Test
    @DisplayName("complete: segmentSeq가 서버 상태와 다르면 CAP-009로 거절한다")
    void complete_rejectsSegmentMismatch() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true);

        assertErrorCode(() -> service.completePartUpload(completeCmd(1, 1, expectedKey(1, 1, "webm"))),
                "CAP-009");
        assertThat(savedParts).isEmpty();
    }

    /* 제출된 s3Key가 서버 재구성 값과 다르면(경로 조작) CAP-009로 거절되는지 검증한다(IDOR 방지). */
    @Test
    @DisplayName("complete: s3Key가 서버 재구성 값과 다르면 CAP-009로 거절한다")
    void complete_rejectsKeyMismatch() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true);

        String tamperedKey = "stt-temp/org-999/meeting-999/segments/0/parts/0001.webm";
        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, tamperedKey)), "CAP-009");
        assertThat(savedParts).isEmpty();
    }

    /* S3에 실제로 그 크기로 업로드된 게 없으면(HEAD 불일치) CAP-019로 거절되는지 검증한다(#155 —
       클라이언트가 업로드도 안 하고 완료를 통보하는 것을 막는다). */
    @Test
    @DisplayName("complete: S3에 실제 업로드가 확인 안 되면 CAP-019로 거절한다")
    void complete_rejectsWhenObjectNotUploaded() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, false);

        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm"))),
                "CAP-019");
        assertThat(savedParts).isEmpty();
    }

    /* 정상 완료 통보면 청크가 저장되고, lastSeq·하트비트가 갱신되는지 검증한다. */
    @Test
    @DisplayName("complete: 정상 통보면 청크를 저장하고 상태·하트비트를 갱신한다")
    void complete_savesChunkAndRefreshesState() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service = service(Optional.of(COMPANY_ID), true, existing, true, true);

        service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm")));

        assertThat(savedParts).hasSize(1);
        RecordingPart saved = savedParts.get(0);
        assertThat(saved.getMeetingId()).isEqualTo(MEETING_ID);
        assertThat(saved.getSeq()).isEqualTo(1);
        assertThat(saved.getContentType()).isEqualTo("audio/webm");
        assertThat(saved.getUploaderMemberId()).isEqualTo(CALLER_ID);
        assertThat(existing.getLastSeq()).isEqualTo(1);
        assertThat(heartbeatRefreshed[0]).isTrue();

        // 저장 용량 미터링에 이 회의의 누적 사용량이 report됨.
        assertThat(reportedUsages).hasSize(1);
        assertThat(reportedUsages.get(0).companyId()).isEqualTo(COMPANY_ID);
        assertThat(reportedUsages.get(0).meetingId()).isEqualTo(MEETING_ID);
        assertThat(reportedUsages.get(0).usedBytes()).isEqualTo(1_000_000L);
    }

    /*
     * 일시정지 중엔 청크를 거부하는지 검증한다 — 10분/40청크 블록 카운터가 일시정지 구간을
     * 안 세려면, 애초에 일시정지 중 통보 자체를 막아야 한다(명세 "10분 카운터는 일시정지
     * 구간을 빼고 이어서 센다").
     */
    @Test
    @DisplayName("complete: 캡처 세션이 일시정지 상태면 CAP-020으로 거절하고 상태를 바꾸지 않는다")
    void complete_rejectsWhenSessionPaused() {
        CaptureUploadState existing = CaptureUploadState.startWithRecorder(MEETING_ID, CALLER_ID);
        CaptureUploadService service =
                service(Optional.of(COMPANY_ID), true, existing, true, true, true);

        assertErrorCode(() -> service.completePartUpload(completeCmd(0, 1, expectedKey(0, 1, "webm"))),
                "CAP-020");

        assertThat(savedParts).isEmpty();
        assertThat(existing.getLastSeq()).isZero();
    }

    // 서버가 재구성할 것으로 기대하는 s3Key(테스트에서 tampered 여부 비교용으로 직접 계산).
    private String expectedKey(int segmentSeq, int seq, String extension) {
        return "stt-temp/org-%d/meeting-%d/segments/%d/parts/%04d.%s"
                .formatted(COMPANY_ID, MEETING_ID, segmentSeq, seq, extension);
    }

    private IssuePartUploadUrlsCommand issueCmd(int count) {
        return new IssuePartUploadUrlsCommand(MEETING_ID, CALLER_ID, count, "audio/webm");
    }

    private CompletePartUploadCommand completeCmd(int segmentSeq, int seq, String s3Key) {
        return new CompletePartUploadCommand(MEETING_ID, CALLER_ID, segmentSeq, seq, s3Key, 1_000_000L);
    }

    // 회의 존재/참석 여부·기존 상태·녹음자 하트비트 생존 여부·S3 objectMatches 결과를 지정해
    // 서비스를 조립한다. 회의는 회사 1 소속으로 고정. state가 null이면 "presign 이력 없음"을 뜻한다.
    // 캡처 세션 일시정지 여부는 지정하지 않으면 기본 false(일시정지 아님)로 조립한다.
    private CaptureUploadService service(Optional<Long> companyId, boolean attendee, CaptureUploadState state,
                                         boolean recorderAlive, boolean objectMatches) {
        return service(companyId, attendee, state, recorderAlive, objectMatches, false, false);
    }

    private CaptureUploadService service(Optional<Long> companyId, boolean attendee, CaptureUploadState state,
                                         boolean recorderAlive, boolean objectMatches, boolean sessionPaused) {
        return service(companyId, attendee, state, recorderAlive, objectMatches, sessionPaused, false, false);
    }

    private CaptureUploadService service(Optional<Long> companyId, boolean attendee, CaptureUploadState state,
                                         boolean recorderAlive, boolean objectMatches, boolean sessionPaused,
                                         boolean overQuota) {
        return service(companyId, attendee, state, recorderAlive, objectMatches, sessionPaused, overQuota, false);
    }

    // overQuota: 저장 용량 한도 초과 여부(StorageQuotaPort 스텁 결과) — 대부분의 시나리오는 상관없어
    // false로 고정한 앞의 오버로드들을 쓰고, 한도 관련 테스트만 이 전체 오버로드를 직접 부른다.
    // quotaLookupThrows: true면 StorageQuotaPort.getStatus가 예외를 던진다(예: 플랜 미설정) —
    // 이때도 녹음이 막히면 안 된다(fail-open)는 걸 검증하는 테스트 전용.
    private CaptureUploadService service(Optional<Long> companyId, boolean attendee, CaptureUploadState state,
                                         boolean recorderAlive, boolean objectMatches, boolean sessionPaused,
                                         boolean overQuota, boolean quotaLookupThrows) {
        savedParts.clear();
        heartbeatRefreshed[0] = false;
        reportedUsages.clear();

        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return companyId.isPresent();
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                return attendee;
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return false;
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
                return Optional.empty();
            }
        };
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> false;
        CapMeetingAccessGuard accessGuard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);

        CaptureUploadStateRepository stateRepo = new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                return Optional.ofNullable(state);
            }

            @Override
            public CaptureUploadState save(CaptureUploadState toSave) {
                return toSave;
            }

            @Override
            public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        RecordingPartRepository partRepo = new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                savedParts.add(recordingPart);
                return recordingPart;
            }

            @Override
            public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                return List.of();
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
            }

            @Override
            public List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq,
                                                                 int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
        CapObjectStoragePort storage = new CapObjectStoragePort() {
            @Override
            public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
                return new IssuedPartUploadUrl("https://stub/upload/" + s3Key, 900);
            }

            @Override
            public IssuedPlaybackUrl issuePlaybackUrl(String s3Key) {
                throw new AssertionError("presign/complete 경로에서 재생 URL은 호출되면 안 됩니다.");
            }

            @Override
            public void deleteRecording(String s3Key) {
                throw new AssertionError("presign/complete 경로에서 삭제는 호출되면 안 됩니다.");
            }

            @Override
            public boolean objectMatches(String s3Key, long expectedSizeBytes) {
                return objectMatches;
            }
        };
        CaptureHeartbeatPort heartbeat = new CaptureHeartbeatPort() {
            @Override
            public void refresh(Long meetingId) {
                heartbeatRefreshed[0] = true;
            }

            @Override
            public boolean isAlive(Long meetingId) {
                return recorderAlive;
            }
        };
        CompletePartUploadWriter writer = new CompletePartUploadWriter(partRepo, stateRepo, heartbeat);
        CapCaptureSessionReferenceRepository sessionRef = meetingId -> sessionPaused;

        // 이 테스트 파일의 시나리오는 전부 40청크 임계값에 한참 못 미치므로(lastSeq가 항상 한 자릿수),
        // 트리거 내부의 실제 파이프라인 포트는 호출되면 안 된다 — 호출되면 테스트가 실패하도록 던진다.
        SttBlockAudioAssemblyPort audioAssemblyPort = new SttBlockAudioAssemblyPort() {
            @Override
            public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq,
                                                     long targetOffsetMs, long availableUpToMs) {
                throw new AssertionError("40청크 미만이므로 호출되면 안 됩니다.");
            }

            @Override
            public String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                                             long startOffsetMs, long endOffsetMs) {
                throw new AssertionError("40청크 미만이므로 호출되면 안 됩니다.");
            }
        };
        SttBlockCutDetectionPort cutDetectionPort = (meetingId, windowAudioS3Key, windowStartOffsetMs,
                                                     targetOffsetMs) -> {
            throw new AssertionError("40청크 미만이므로 호출되면 안 됩니다.");
        };
        CreateSttBlockPort createSttBlockPort = command -> {
            throw new AssertionError("40청크 미만이므로 호출되면 안 됩니다.");
        };
        SttBlockFormedWriter sttBlockFormedWriter = new SttBlockFormedWriter(stateRepo);
        SttBlockCutTrigger sttBlockCutTrigger = new SttBlockCutTrigger(
                audioAssemblyPort, cutDetectionPort, createSttBlockPort, stateRepo, sttBlockFormedWriter);

        StorageQuotaPort storageQuotaPort = quotaCompanyId -> {
            if (quotaLookupThrows) {
                throw new BusinessException(MeteringErrorCode.MT_STORAGE_PLAN_NOT_FOUND);
            }
            return new StorageQuotaStatusResult(quotaCompanyId, 0L, 1L, overQuota);
        };
        ReportMeetingStorageUsagePort reportPort = command -> reportedUsages.add(command);

        return new CaptureUploadService(meetingRef, accessGuard, stateRepo, storage, heartbeat, sessionRef, writer,
                sttBlockCutTrigger, storageQuotaPort, reportPort, FIXED_CLOCK);
    }

    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
