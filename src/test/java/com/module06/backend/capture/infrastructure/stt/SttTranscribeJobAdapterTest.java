package com.module06.backend.capture.infrastructure.stt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.ConflictException;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscribeException;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJob;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJobStatus;

import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.port.out.SttJobPort.SttJob;
import com.module06.backend.capture.application.service.MeetingAttendeeCountProvider;
import com.module06.backend.capture.domain.model.VocabularyStatus;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AWS Transcribe 제출 어댑터.
 *
 * <p>검증의 축은 <b>요청에 무엇이 들어가고 무엇이 안 들어가는가</b>다. 특히 화자 분리(V5.23)는
 * 화자 판정의 유일한 입력이라, 조용히 빠지면 전사는 멀쩡한데 그 뒤 화자만 전부 NULL 이 된다.
 * 그 실패는 어디에도 안 드러나므로 테스트로 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SttTranscribeJobAdapterTest {

    private static final String BUCKET = "zebra-storage-prod";
    private static final String AUDIO_KEY = "stt-temp/org-1/meeting-500/blocks/3.wav";

    @Mock
    private TranscribeClient transcribeClient;

    @Mock
    private MeetingVocabularyRepository vocabularyRepository;

    @Mock
    private MeetingAttendeeCountProvider attendeeCountProvider;

    @Captor
    private ArgumentCaptor<StartTranscriptionJobRequest> requestCaptor;

    private SttTranscribeJobAdapter adapter() {
        return new SttTranscribeJobAdapter(transcribeClient, vocabularyRepository,
                attendeeCountProvider, new SttTranscribeProperties(BUCKET, "stt-out/"));
    }

    @Test
    @DisplayName("잡 이름·언어·포맷·미디어 URI·결과 위치를 그대로 보낸다")
    void 제출_요청을_구성한다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        StartTranscriptionJobRequest request = requestCaptor.getValue();

        assertThat(request.transcriptionJobName()).isEqualTo("meeting-500-block-3-r0");
        assertThat(request.languageCode()).isEqualTo(LanguageCode.KO_KR);
        // 확장자 추측에 맡기지 않는다 — 조립 포맷이 바뀌면 그 사실이 여기서 드러나야 한다.
        assertThat(request.mediaFormat()).isEqualTo(MediaFormat.WAV);
        assertThat(request.media().mediaFileUri()).isEqualTo("s3://" + BUCKET + "/" + AUDIO_KEY);
        assertThat(request.outputBucketName()).isEqualTo(BUCKET);
    }

    @Test
    @DisplayName("미디어 포맷을 확장자에서 정한다 — wav 로 못 박으면 수동 업로드가 전부 거절된다")
    void 포맷을_확장자에서_정한다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        // 수동 업로드(CAP-10)는 사용자가 올린 파일이다. m4a 는 같은 컨테이너인 MP4 로 보낸다.
        adapter().submit(new SttJob(500L, 0, "aws-transcribe", "meeting-500-block-0-r0",
                "recordings/org-1/meeting-500/회의녹음.m4a", 0, 0));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().mediaFormat()).isEqualTo(MediaFormat.MP4);
    }

    @Test
    @DisplayName("대소문자를 가리지 않는다 — .WAV 를 모르는 확장자로 보면 정상 파일이 판정 없이 나간다")
    void 확장자_대소문자를_가리지_않는다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        adapter().submit(new SttJob(500L, 0, "aws-transcribe", "meeting-500-block-0-r0",
                "recordings/org-1/meeting-500/REC.WAV", 0, 0));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().mediaFormat()).isEqualTo(MediaFormat.WAV);
    }

    @Test
    @DisplayName("모르는 확장자면 포맷을 빼고 보낸다 — 틀린 값을 보내 거절당하는 것보다 낫다")
    void 모르는_확장자는_제공자_판정에_맡긴다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        adapter().submit(new SttJob(500L, 0, "aws-transcribe", "meeting-500-block-0-r0",
                "recordings/org-1/meeting-500/녹음파일", 0, 0));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        // 필드가 없으면 Transcribe 가 스스로 판정한다.
        assertThat(requestCaptor.getValue().mediaFormat()).isNull();
    }

    @Test
    @DisplayName("결과 키는 잡 이름으로 만든다 — 사용자 파일명이 한 글자도 섞이지 않는다")
    void 결과_키를_잡_이름으로_만든다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().outputKey())
                .isEqualTo("stt-out/meeting-500-block-3-r0.json");
    }

    @Test
    @DisplayName("한글 파일명을 올려도 결과 키에 섞이지 않는다 — 2026-08-15 운영 정지의 원인")
    void 한글_파일명이_결과_키를_오염시키지_않는다() {
        /*
         * Transcribe 의 outputKey 는 [a-zA-Z0-9-_.!*'()/&$@=;:+,? \x00-\x1F\x7F] 만 허용한다.
         * 한글이 없다 — 예전처럼 오디오 키에서 파생시키면 제출이 400 으로 거절되고, 그 실패를
         * cap 의 best-effort 트리거가 삼켜 "제출 완료"로 보인 채 요약이 영원히 안 나온다.
         */
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        adapter().submit(new SttJob(500L, 0, "aws-transcribe", "meeting-500-block-0-r0",
                "recordings/org-11/member-9/online-pending/1a253c0e/음성 260814_124512.m4a",
                0, 60_000));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        StartTranscriptionJobRequest request = requestCaptor.getValue();

        assertThat(request.outputKey())
                .isEqualTo("stt-out/meeting-500-block-0-r0.json")
                .containsOnlyOnce("stt-out/")
                .matches("[a-zA-Z0-9\\-_.!*'()/&$@=;:+,? ]{1,1024}");

        // 오디오 URI 쪽은 원문 그대로다 — 그건 Transcribe 가 S3 키로 받는 값이라 제약이 다르다.
        assertThat(request.media().mediaFileUri()).endsWith("음성 260814_124512.m4a");
    }

    @Test
    @DisplayName("재시도하면 결과 키도 달라진다 — 잡 이름에 retryCount 가 들어 있다")
    void 재시도마다_결과_키가_다르다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r2"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        // 이전 회차 결과를 덮어쓰지 않는다 — 무엇을 읽었는지 되짚을 수 있어야 한다.
        assertThat(requestCaptor.getValue().outputKey())
                .isEqualTo("stt-out/meeting-500-block-3-r2.json");
    }

    @Test
    @DisplayName("⚠ 화자 분리를 켜고 상한을 참석자 수로 준다 — 이게 빠지면 화자가 전부 NULL 이 된다")
    void 화자_분리를_켠다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.of(readyVocabulary()));
        when(attendeeCountProvider.attendeeCountOf(500L)).thenReturn(OptionalInt.of(4));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().settings().showSpeakerLabels()).isTrue();
        // 넉넉히 주면 한 사람이 여러 라벨로 쪼개져 닻이 안 붙는다. 명단 그대로가 상한이다.
        assertThat(requestCaptor.getValue().settings().maxSpeakerLabels()).isEqualTo(4);
    }

    @Test
    @DisplayName("어휘가 없어도 화자 분리는 켜진다 — 둘은 아무 관계도 없다")
    void 어휘가_없어도_화자_분리는_켜진다() {
        /*
         * 예전에는 어휘가 READY 일 때만 Settings 를 만들었다. 그 구조를 그대로 두고 화자 분리를
         * 얹으면 어휘가 없는 회의에서 화자 분리도 함께 조용히 꺼진다.
         */
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(attendeeCountProvider.attendeeCountOf(500L)).thenReturn(OptionalInt.of(3));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().settings().showSpeakerLabels()).isTrue();
        assertThat(requestCaptor.getValue().settings().vocabularyName()).isNull();
    }

    @Test
    @DisplayName("참석자 수를 모르면 기본 상한으로 켠다 — 끄면 그 회의는 화자가 확정적으로 NULL 이다")
    void 참석자_수를_모르면_기본_상한을_쓴다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(attendeeCountProvider.attendeeCountOf(500L)).thenReturn(OptionalInt.empty());

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        /*
         * 넉넉한 쪽으로 틀린다. 넘치면 한 사람이 여러 라벨로 쪼개져 그 발화가 NULL 로 남을
         * 뿐이지만, 모자라면 두 사람이 한 라벨로 합쳐져 오귀속이 확정으로 저장된다.
         */
        assertThat(requestCaptor.getValue().settings().maxSpeakerLabels()).isEqualTo(10);
    }

    @Test
    @DisplayName("참석자 조회가 터져도 화자 분리를 끄지 않는다")
    void 참석자_조회_실패가_화자_분리를_끄지_않는다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(attendeeCountProvider.attendeeCountOf(500L)).thenThrow(new IllegalStateException("DB 흔들림"));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().settings().showSpeakerLabels()).isTrue();
        assertThat(requestCaptor.getValue().settings().maxSpeakerLabels()).isEqualTo(10);
    }

    @Test
    @DisplayName("상한 범위 밖은 가둔다 — 제공자가 거절하면 받아쓰기가 통째로 실패한다")
    void 상한을_범위_안에_가둔다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());

        // 명단이 1명이어도 끄지 않는다 — 명단이 덜 채워졌을 뿐 여러 사람이 말한 회의일 수 있다.
        when(attendeeCountProvider.attendeeCountOf(500L)).thenReturn(OptionalInt.of(1));
        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        when(attendeeCountProvider.attendeeCountOf(500L)).thenReturn(OptionalInt.of(80));
        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r1"));

        verify(transcribeClient, times(2)).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(request -> request.settings().maxSpeakerLabels())
                .containsExactly(2, 30);
    }

    @Test
    @DisplayName("어휘가 READY 면 이름을 붙인다")
    void 어휘가_준비되면_붙인다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.of(readyVocabulary()));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().settings().vocabularyName()).isEqualTo("meeting-500-vocab");
    }

    @Test
    @DisplayName("어휘가 PENDING 이면 붙이지 않고 그대로 제출한다 — READY 가 아니어도 녹음은 성립한다")
    void 어휘가_준비_전이면_없이_제출한다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.of(
                new VocabularyView(1L, 500L, VocabularyStatus.PENDING, 0, "meeting-500-vocab", null, null, false, null)));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        /*
         * PENDING 이름을 보내면 Transcribe 가 거절해 제출 자체가 실패한다. 그런데 명세는
         * "READY 가 아니어도 녹음은 시작할 수 있다"이고 그 뜻은 인식률만 낮아진다는 것이다 —
         * 어휘가 늦었다는 이유로 받아쓰기가 통째로 실패하면 그 계약이 깨진다.
         */
        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        // Settings 자체는 붙는다(화자 분리 때문에). "어휘 없이 돌았다"는 이름이 빈 것으로 읽는다.
        assertThat(requestCaptor.getValue().settings().vocabularyName()).isNull();
    }

    @Test
    @DisplayName("어휘 조회가 터져도 제출은 한다")
    void 어휘_조회_실패는_제출을_막지_않는다() {
        when(vocabularyRepository.findByMeeting(500L)).thenThrow(new IllegalStateException("DB 흔들림"));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().settings().vocabularyName()).isNull();
        // 어휘를 못 읽은 것이 화자 분리까지 끄면 안 된다.
        assertThat(requestCaptor.getValue().settings().showSpeakerLabels()).isTrue();
    }

    @Test
    @DisplayName("지원하지 않는 제공자는 400 — 다른 제공자로 대신 돌리지 않는다")
    void 모르는_제공자는_막는다() {
        assertThatThrownBy(() -> adapter().submit(job("whisper", "meeting-500-block-3-r1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.STT_PROVIDER_UNSUPPORTED);
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        // 요청한 것과 다른 제공자로 돌아가면 "제공자를 바꿔봤다"는 판단의 근거가 거짓이 된다.
        verifyNoInteractions(transcribeClient);
    }

    @Test
    @DisplayName("이름이 충돌해도 이미 있는 잡이 같은 오디오면 채택한다 — 같은 구간을 두 번 전사하지 않는다")
    void 같은_오디오를_가리키는_기존_잡은_채택한다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(ConflictException.builder().message("job exists").build());
        when(transcribeClient.getTranscriptionJob(any(GetTranscriptionJobRequest.class)))
                .thenReturn(existingJob("s3://" + BUCKET + "/" + AUDIO_KEY,
                        TranscriptionJobStatus.COMPLETED));

        // 던지지 않는다. 블록은 QUEUED 로 남고 폴링이 이 이름으로 결과를 가져간다 —
        // 제출이 도달했는데 응답을 못 받아 우리 행만 롤백된 상태(2026-08-18 meeting-2)의 복구다.
        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).getTranscriptionJob(GetTranscriptionJobRequest.builder()
                .transcriptionJobName("meeting-500-block-3-r0")
                .build());
    }

    @Test
    @DisplayName("기존 잡이 FAILED 여도 채택한다 — 폴링이 블록을 FAILED 로 닫아야 재처리를 누를 수 있다")
    void 실패한_기존_잡도_채택한다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(ConflictException.builder().message("job exists").build());
        when(transcribeClient.getTranscriptionJob(any(GetTranscriptionJobRequest.class)))
                .thenReturn(existingJob("s3://" + BUCKET + "/" + AUDIO_KEY,
                        TranscriptionJobStatus.FAILED));

        // 여기서 실패로 올리면 그 블록은 아예 만들어지지 않아(트랜잭션 롤백) 재처리 대상도 못 된다.
        // 채택해야 FAILED 로 닫히고, 그때 -r1 이라는 새 이름으로 다시 돌릴 수 있다.
        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));
    }

    @Test
    @DisplayName("기존 잡이 다른 오디오를 가리키면 채택하지 않는다 — 남의 전사가 이 회의에 붙는다")
    void 다른_오디오를_가리키는_기존_잡은_실패로_올린다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(ConflictException.builder().message("job exists").build());
        // 재시드로 meetingId 가 재사용되면 이름은 같은데 오디오가 다르다 — 이름만으로는 구분되지 않는다.
        when(transcribeClient.getTranscriptionJob(any(GetTranscriptionJobRequest.class)))
                .thenReturn(existingJob("s3://" + BUCKET + "/stt-temp/org-9/meeting-500/blocks/3.wav",
                        TranscriptionJobStatus.COMPLETED));

        assertThatThrownBy(() -> adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.STT_SUBMIT_FAILED);
    }

    @Test
    @DisplayName("기존 잡을 조회하지 못하면 채택하지 않는다 — 우리 녹음이라는 근거가 없다")
    void 기존_잡_조회에_실패하면_실패로_올린다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(ConflictException.builder().message("job exists").build());
        when(transcribeClient.getTranscriptionJob(any(GetTranscriptionJobRequest.class)))
                .thenThrow(TranscribeException.builder().message("throttled").build());

        assertThatThrownBy(() -> adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.STT_SUBMIT_FAILED);
    }

    @Test
    @DisplayName("제출 실패는 502 로 올린다 — 삼키면 QUEUED 로 영원히 남는다")
    void 제출_실패는_예외를_올린다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(TranscribeException.builder().message("throttled").build());

        assertThatThrownBy(() -> adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.STT_SUBMIT_FAILED);
                    // 우리 버그가 아니라 제공자가 접수하지 못한 것이다.
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    private static SttJob job(String provider, String jobName) {
        return new SttJob(500L, 3, provider, jobName, AUDIO_KEY, 1_794_000, 2_390_000);
    }

    private static GetTranscriptionJobResponse existingJob(String mediaFileUri,
                                                           TranscriptionJobStatus status) {
        return GetTranscriptionJobResponse.builder()
                .transcriptionJob(TranscriptionJob.builder()
                        .transcriptionJobName("meeting-500-block-3-r0")
                        .transcriptionJobStatus(status)
                        .media(Media.builder().mediaFileUri(mediaFileUri).build())
                        .build())
                .build();
    }

    private static VocabularyView readyVocabulary() {
        return new VocabularyView(1L, 500L, VocabularyStatus.READY, 214,
                "meeting-500-vocab", LocalDateTime.of(2026, 8, 4, 9, 12), null, false, null);
    }
}
