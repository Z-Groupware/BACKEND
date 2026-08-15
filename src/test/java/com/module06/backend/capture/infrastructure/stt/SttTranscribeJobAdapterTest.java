package com.module06.backend.capture.infrastructure.stt;

import java.time.LocalDateTime;
import java.util.Optional;

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
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscribeException;

import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.port.out.SttJobPort.SttJob;
import com.module06.backend.capture.domain.model.VocabularyStatus;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AWS Transcribe 제출 어댑터.
 *
 * <p>검증의 축은 <b>요청에 무엇이 들어가고 무엇이 안 들어가는가</b>다. 특히 화자 분리를 켜지
 * 않는 것은 이 제품의 설계 결정이라(음량 기반 귀속으로 대체) 실수로 켜지면 요금이 오르고
 * 믿지 않기로 한 신호가 응답에 섞인다 — 테스트로 못 박는다.
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

    @Captor
    private ArgumentCaptor<StartTranscriptionJobRequest> requestCaptor;

    private SttTranscribeJobAdapter adapter() {
        return new SttTranscribeJobAdapter(transcribeClient, vocabularyRepository,
                new SttTranscribeProperties(BUCKET, "stt-out/"));
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
    @DisplayName("⚠ 화자 분리를 켜지 않는다 — 음량 기반 귀속으로 대체한 설계 결정이다")
    void 화자_분리를_켜지_않는다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.of(readyVocabulary()));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        /*
         * 켜면 요금이 오르고, 믿지 않기로 한 신호가 응답에 섞인다 — 나중에 누가 "있으니까" 쓰면
         * 화자 판정이 두 갈래가 되고 둘이 다를 때 판단 근거가 없다.
         */
        assertThat(requestCaptor.getValue().settings().showSpeakerLabels()).isNull();
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
        assertThat(requestCaptor.getValue().settings()).isNull();
    }

    @Test
    @DisplayName("어휘 조회가 터져도 제출은 한다")
    void 어휘_조회_실패는_제출을_막지_않는다() {
        when(vocabularyRepository.findByMeeting(500L)).thenThrow(new IllegalStateException("DB 흔들림"));

        adapter().submit(job("aws-transcribe", "meeting-500-block-3-r0"));

        verify(transcribeClient).startTranscriptionJob(requestCaptor.capture());
        assertThat(requestCaptor.getValue().settings()).isNull();
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
    @DisplayName("잡 이름 충돌은 성공으로 삼키지 않는다 — 우리 상태와 제공자 상태가 어긋난 것이다")
    void 잡_이름_충돌은_실패로_올린다() {
        when(vocabularyRepository.findByMeeting(500L)).thenReturn(Optional.empty());
        when(transcribeClient.startTranscriptionJob(any(StartTranscriptionJobRequest.class)))
                .thenThrow(ConflictException.builder().message("job exists").build());

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

    private static VocabularyView readyVocabulary() {
        return new VocabularyView(1L, 500L, VocabularyStatus.READY, 214,
                "meeting-500-vocab", LocalDateTime.of(2026, 8, 4, 9, 12), null, false, null);
    }
}
