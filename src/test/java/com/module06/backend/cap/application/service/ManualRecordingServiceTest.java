package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-10 수동 녹음 업로드 서비스의 회의 존재·Host 검증·s3Key 검증·중복 제출·STT 트리거 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("CAP-10 수동 녹음 업로드 서비스")
class ManualRecordingServiceTest {

    private static final String VALID_KEY = "recordings/org-1/meeting-500/recording.ogg";

    // 저장된 녹음본과 STT 트리거 여부를 기록한다(테스트별로 새로 만든다).
    private final Recording[] savedRecording = new Recording[1];
    private final boolean[] sttTriggered = new boolean[1];

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
        ManualRecordingService service = new ManualRecordingService(meetingRef, recordingRepo, sttPort);

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
        return new ManualRecordingService(meetingRef, recordingRepo, sttPort);
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
