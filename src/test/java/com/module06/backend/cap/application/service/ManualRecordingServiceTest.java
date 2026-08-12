package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;

/*
 * CAP-10 수동 녹음 업로드 서비스의 회의 존재·Host 검증·s3Key 검증·중복 제출·STT 트리거 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("CAP-10 수동 녹음 업로드 서비스")
class ManualRecordingServiceTest {

    private static final String VALID_KEY = "recordings/org-1/meeting-500/recording.ogg";

    // 저장된 녹음본·STT 트리거·저장 용량 report 여부를 기록한다(테스트별로 새로 만든다).
    private final Recording[] savedRecording = new Recording[1];
    private final boolean[] sttTriggered = new boolean[1];
    private final ReportMeetingStorageUsageCommand[] reportedUsage = new ReportMeetingStorageUsageCommand[1];

    /* 회의가 없으면 CAP-002로 거절하는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-002로 거절한다")
    void rejectsWhenMeetingMissing() {
        ManualRecordingService service = service(Optional.empty(), true, false);

        assertErrorCode(() -> service.registerManualRecording(cmd(VALID_KEY, 15_000_000L)), "CAP-002");
        assertThat(sttTriggered[0]).isFalse();
    }

    /* Host가 아니면 CAP-013으로 거절하는지 검증한다. */
    @Test
    @DisplayName("Host가 아니면 CAP-013으로 거절한다")
    void rejectsWhenNotHost() {
        ManualRecordingService service = service(Optional.of(1L), false, false);

        assertErrorCode(() -> service.registerManualRecording(cmd(VALID_KEY, 15_000_000L)), "CAP-013");
        assertThat(sttTriggered[0]).isFalse();
    }

    /* s3Key가 기대 경로 접두를 벗어나면 CAP-015로 거절하는지 검증한다(타 회사·경로 조작 차단). */
    @Test
    @DisplayName("s3Key가 기대 경로가 아니면 CAP-015로 거절한다")
    void rejectsWhenKeyMismatches() {
        ManualRecordingService service = service(Optional.of(1L), true, false);

        // 다른 회사(org-2)
        assertErrorCode(() -> service.registerManualRecording(
                cmd("recordings/org-2/meeting-500/recording.ogg", 100L)), "CAP-015");
        // 임시 카테고리(stt-temp)
        assertErrorCode(() -> service.registerManualRecording(
                cmd("stt-temp/org-1/meeting-500/recording.ogg", 100L)), "CAP-015");
        // 경로 조작
        assertErrorCode(() -> service.registerManualRecording(
                cmd("recordings/org-1/meeting-500/../../x.ogg", 100L)), "CAP-015");
        // 파일명 없음(슬래시로 끝남)
        assertErrorCode(() -> service.registerManualRecording(
                cmd("recordings/org-1/meeting-500/", 100L)), "CAP-015");
        assertThat(sttTriggered[0]).isFalse();
    }

    /* 이미 제출된 녹음이 있으면 CAP-014로 거절하는지 검증한다. */
    @Test
    @DisplayName("이미 제출된 녹음이 있으면 CAP-014로 거절한다")
    void rejectsWhenAlreadySubmitted() {
        ManualRecordingService service = service(Optional.of(1L), true, true);

        assertErrorCode(() -> service.registerManualRecording(cmd(VALID_KEY, 15_000_000L)), "CAP-014");
        assertThat(sttTriggered[0]).isFalse();
    }

    /* 정상: 메타를 저장하고 STT를 트리거하며 durationMs=0·status=DONE을 반환하는지 검증한다. */
    @Test
    @DisplayName("정상 등록 시 저장·STT 트리거 후 DONE을 반환한다")
    void registersAndTriggersStt() {
        ManualRecordingService service = service(Optional.of(1L), true, false);

        RegisterManualRecordingUseCase.Result result =
                service.registerManualRecording(cmd(VALID_KEY, 15_000_000L));

        // 응답: durationMs는 파이프라인이 채우므로 0, status는 고정 DONE.
        assertThat(result.meetingId()).isEqualTo(500L);
        assertThat(result.durationMs()).isZero();
        assertThat(result.sizeBytes()).isEqualTo(15_000_000L);
        assertThat(result.status()).isEqualTo("DONE");

        // 저장된 메타: fileUrl=s3Key, fileName은 경로 마지막 세그먼트, durationSec는 아직 null.
        assertThat(savedRecording[0]).isNotNull();
        assertThat(savedRecording[0].getMeetingId()).isEqualTo(500L);
        assertThat(savedRecording[0].getFileUrl()).isEqualTo(VALID_KEY);
        assertThat(savedRecording[0].getFileName()).isEqualTo("recording.ogg");
        assertThat(savedRecording[0].getSizeBytes()).isEqualTo(15_000_000L);
        assertThat(savedRecording[0].getDurationSec()).isNull();

        // STT 트리거됨.
        assertThat(sttTriggered[0]).isTrue();

        // 저장 용량 미터링에 첨부 파일 크기로 report됨.
        assertThat(reportedUsage[0]).isNotNull();
        assertThat(reportedUsage[0].companyId()).isEqualTo(1L);
        assertThat(reportedUsage[0].meetingId()).isEqualTo(500L);
        assertThat(reportedUsage[0].usedBytes()).isEqualTo(15_000_000L);
        // 생성 report의 revision은 항상 1(CREATE_REVISION) — 벽시계를 쓰지 않는다.
        assertThat(reportedUsage[0].revision()).isEqualTo(1L);
    }

    /*
     * STT 트리거(MeetingRecordingSttPort)가 실패해도(예: AWS Transcribe 제출 오류) 등록 자체는
     * 롤백되지 않는지 검증한다 — best-effort 계약(SttBlockCreationService 주석 참고)이 실 어댑터
     * 전환 후에도 실제로 지켜지는지 확인하는 회귀 테스트다.
     */
    @Test
    @DisplayName("STT 트리거가 실패해도 녹음 등록은 그대로 완료된다")
    void registersEvenWhenSttTriggerFails() {
        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return true;
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
                return Optional.of(1L);
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
        RecordingRepository recordingRepo = new RecordingRepository() {
            @Override
            public Recording save(Recording recording) {
                savedRecording[0] = recording;
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
        };
        boolean[] sttPortCalled = new boolean[1];
        MeetingRecordingSttPort failingSttPort = (meetingId, s3Key) -> {
            sttPortCalled[0] = true;
            throw new RuntimeException("AWS Transcribe 제출 실패(가정)");
        };
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> false;
        CapMeetingAccessGuard accessGuard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);
        ReportMeetingStorageUsagePort storagePort = command -> reportedUsage[0] = command;
        ManualRecordingService service =
                new ManualRecordingService(meetingRef, accessGuard, recordingRepo, failingSttPort, storagePort);

        RegisterManualRecordingUseCase.Result result =
                service.registerManualRecording(cmd(VALID_KEY, 15_000_000L));

        // STT 포트가 실제로 호출됐는지부터 확인한다(CodeRabbit 지적 — 호출을 통째로 지워도
        // 통과하던 허점).
        assertThat(sttPortCalled[0]).isTrue();
        assertThat(result.status()).isEqualTo("DONE");
        assertThat(savedRecording[0]).isNotNull();
        // STT는 실패했지만 저장 용량 report는 여전히 이어져야 한다(STT 실패가 뒤 단계를 막지 않음).
        assertThat(reportedUsage[0]).isNotNull();
    }

    /* 선검사(existsByMeetingId)를 통과한 뒤 저장 단계에서 UNIQUE 위반으로 CAP-014가 나는 경쟁 경로도
       그대로 전파하고, STT는 트리거되지 않는지(save가 트리거보다 앞) 검증한다. */
    @Test
    @DisplayName("저장 단계 UNIQUE 위반(경쟁)도 CAP-014로 전파하고 STT를 트리거하지 않는다")
    void propagatesSaveTimeDuplicateAndSkipsStt() {
        sttTriggered[0] = false;
        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return true;
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
                return Optional.of(1L);
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
        // 선검사는 통과(false)하지만 저장에서 제약위반 → 어댑터가 CAP-014로 변환하는 상황을 재현.
        RecordingRepository recordingRepo = new RecordingRepository() {
            @Override
            public Recording save(Recording recording) {
                throw new BusinessException(CapErrorCode.CAP_RECORDING_ALREADY_SUBMITTED);
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
        };
        MeetingRecordingSttPort sttPort = (meetingId, s3Key) -> sttTriggered[0] = true;
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> false;
        CapMeetingAccessGuard accessGuard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);
        ReportMeetingStorageUsagePort storagePort = command -> reportedUsage[0] = command;
        ManualRecordingService service = new ManualRecordingService(meetingRef, accessGuard, recordingRepo, sttPort,
                storagePort);

        assertErrorCode(() -> service.registerManualRecording(cmd(VALID_KEY, 100L)), "CAP-014");
        assertThat(sttTriggered[0]).isFalse();
    }

    // meetingId 500, callerId 7 고정 명령.
    private RegisterManualRecordingCommand cmd(String s3Key, long sizeBytes) {
        return new RegisterManualRecordingCommand(500L, 7L, s3Key, sizeBytes);
    }

    // 회의 companyId(존재 여부)·Host 여부·중복 제출 여부를 지정해 서비스를 조립한다. STT 트리거·저장을 기록한다.
    private ManualRecordingService service(Optional<Long> companyId, boolean host, boolean alreadySubmitted) {
        savedRecording[0] = null;
        sttTriggered[0] = false;
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
                return host;
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
        RecordingRepository recordingRepo = new RecordingRepository() {
            @Override
            public Recording save(Recording recording) {
                savedRecording[0] = recording;
                return recording;
            }

            @Override
            public boolean existsByMeetingId(Long meetingId) {
                return alreadySubmitted;
            }

            @Override
            public Optional<Recording> findByMeetingId(Long meetingId) {
                return Optional.empty();
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
            }
        };
        MeetingRecordingSttPort sttPort = (meetingId, s3Key) -> sttTriggered[0] = true;
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> false;
        CapMeetingAccessGuard accessGuard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);
        ReportMeetingStorageUsagePort storagePort = command -> reportedUsage[0] = command;
        return new ManualRecordingService(meetingRef, accessGuard, recordingRepo, sttPort, storagePort);
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
