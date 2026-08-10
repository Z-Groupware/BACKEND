package com.module06.backend.capture.infrastructure.stt;

import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.ConflictException;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.Settings;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscribeException;

import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.port.out.SttJobPort;
import com.module06.backend.capture.domain.model.VocabularyStatus;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * SttJobPort 의 AWS Transcribe 구현 — StartTranscriptionJob 제출까지다.
 *
 * <h2>제출만 한다. 결과는 아직 아무도 안 가져온다</h2>
 * Transcribe 는 잡을 접수하고 즉시 돌아온다. 상태 전이(QUEUED → RUNNING → DONE)와 결과 적재는
 * **폴링이 하는 일이고 그건 후속이다.** 그래서 이 어댑터가 붙어도 블록은 QUEUED 에 머문다 —
 * 스텁이 예고해 둔 그 상태 그대로다. 다만 이제 **제공자 쪽에는 실제로 잡이 생긴다**는 점이
 * 다르다. 폴링이 붙기 전까지는 AWS 콘솔이 유일한 확인 경로다.
 *
 * <h2>화자 분리(diarization)를 켜지 않는다 — 설계 결정이다</h2>
 * Transcribe 의 ShowSpeakerLabels 는 목소리로 화자를 추정한다. 이 제품은 그 문제를 **기기 문제로
 * 바꿔** 풀기로 했다 — 참석자 각자 브라우저가 자기 person_id 와 마이크 음량(rms)을 실어 보내고,
 * L1 이 시간창을 맞춰 음량이 가장 큰 사람을 화자로 정한다(SpeakerAttributionResolver).
 *
 * 켜면 두 가지가 나빠진다. 요금이 오르고, **믿지 않기로 한 신호가 응답에 섞인다** — 나중에
 * 누군가 그 값을 "있으니까" 쓰기 시작하면 화자 판정이 두 갈래가 되고, 둘이 다를 때 어느 쪽이
 * 맞는지 판단할 근거가 없다. 쓰지 않을 값은 받지 않는다.
 *
 * <h2>언어를 상수로 둔다</h2>
 * ko-KR 고정이다. 프로퍼티로 빼면 "설정할 수 있다"는 뜻이 되는데, 이 제품의 프롬프트·어휘·
 * 지시어 해소가 전부 한국어 전제로 쓰여 있어 값을 바꿔도 파이프라인이 성립하지 않는다.
 * 진짜로 다국어를 하게 되면 이 상수가 아니라 계층 전체가 바뀐다(LayerLiveness 가 유예 시간을
 * 상수로 둔 것과 같은 판단 — 바꿀 수 있게 두는 것 자체가 거짓말이 되는 자리다).
 *
 * <h2>MediaFormat 은 키 확장자에서 정한다 — 하드코딩하지 않는다</h2>
 * 자동 블록은 ffmpeg 이 만든 wav 지만(SttBlockAudioAssemblyS3FfmpegAdapter), **수동 업로드
 * (CAP-10)는 사용자가 올린 파일**이라 mp3·m4a·webm 이 그대로 들어온다. wav 로 못 박으면 그
 * 경로가 전부 제출에서 거절된다 — 처음에 wav 로 박아 뒀던 것이 이 자리의 버그였다.
 *
 * 그래도 **아는 것은 명시한다.** 확장자를 알아보면 그 값을 보내고, 모르는 확장자면 필드를 빼
 * Transcribe 가 스스로 판정하게 둔다. 전부 추측에 맡기지 않는 이유 — 조립 포맷이 바뀌었을 때
 * 그 사실이 여기 드러나지 않고 인식 결과만 조용히 나빠진다.
 */
@Slf4j
@Component
@Profile("prod")
public class SttTranscribeJobAdapter implements SttJobPort {

    /* 이 어댑터가 답하는 제공자. STT-04 가 다른 값을 보내면 여기서 막는다. */
    static final String PROVIDER = "aws-transcribe";

    private static final LanguageCode LANGUAGE = LanguageCode.KO_KR;

    /*
     * 확장자 → Transcribe 포맷. 이 목록에 없는 확장자는 필드를 아예 빼서 제공자가 판정한다.
     *
     * m4a 를 MP4 로 보내는 이유 — 같은 컨테이너다(Transcribe 에 M4A 값이 없다). 확장자만 다르고
     * 내용이 같아서, 여기서 안 매핑하면 아이폰 녹음 파일이 전부 판정 없이 나간다.
     */
    private static final Map<String, MediaFormat> FORMAT_BY_EXTENSION = Map.of(
            "wav", MediaFormat.WAV,
            "mp3", MediaFormat.MP3,
            "mp4", MediaFormat.MP4,
            "m4a", MediaFormat.MP4,
            "flac", MediaFormat.FLAC,
            "ogg", MediaFormat.OGG,
            "webm", MediaFormat.WEBM,
            "amr", MediaFormat.AMR);

    private final TranscribeClient transcribeClient;
    private final MeetingVocabularyRepository vocabularyRepository;
    private final String bucket;
    private final String outputPrefix;

    public SttTranscribeJobAdapter(TranscribeClient transcribeClient,
                                   MeetingVocabularyRepository vocabularyRepository,
                                   SttTranscribeProperties properties) {
        this.transcribeClient = transcribeClient;
        this.vocabularyRepository = vocabularyRepository;
        this.bucket = properties.bucket();
        this.outputPrefix = properties.outputPrefix();
    }

    @Override
    public void submit(SttJob job) {
        /*
         * 제공자가 다르면 **여기서 막는다.** STT-04 는 `{"provider":"whisper"}` 를 받을 수 있고
         * (실패한 블록을 다른 제공자로 돌리는 것이 그 API 의 목적 중 하나다), whisper 어댑터는
         * 아직 없다. 그대로 Transcribe 에 보내면 사용자가 요청한 것과 다른 제공자로 돌아가고,
         * 결과가 나빠도 "whisper 로 돌렸는데 안 낫네"로 읽힌다 — 판단의 근거가 거짓이 된다.
         */
        if (!PROVIDER.equals(job.provider())) {
            log.warn("지원하지 않는 STT 제공자 — meetingId={} blockSeq={} provider={}",
                    job.meetingId(), job.blockSeq(), job.provider());
            throw new BusinessException(CaptureErrorCode.STT_PROVIDER_UNSUPPORTED);
        }

        String outputKey = outputKeyOf(job.audioS3Key());
        Settings settings = settingsFor(job.meetingId());

        StartTranscriptionJobRequest.Builder request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(job.providerJobName())
                .languageCode(LANGUAGE)
                .media(Media.builder().mediaFileUri("s3://" + bucket + "/" + job.audioS3Key()).build())
                .outputBucketName(bucket)
                .outputKey(outputKey);

        MediaFormat mediaFormat = mediaFormatOf(job.audioS3Key());
        if (mediaFormat != null) {
            request.mediaFormat(mediaFormat);
        } else {
            // 모르는 확장자다. 필드를 빼면 Transcribe 가 스스로 판정한다 — 틀린 값을 보내
            // 거절당하는 것보다 낫다. 어떤 확장자가 들어오는지는 로그로 쌓아 매핑을 늘린다.
            log.info("확장자로 미디어 포맷을 정하지 못했다 — 제공자 판정에 맡긴다. s3Key={}",
                    job.audioS3Key());
        }

        // 어휘가 READY 가 아니면 Settings 를 아예 붙이지 않는다 — 빈 Settings 를 보내는 것과
        // 뜻이 같지만, 붙이지 않는 편이 "이 잡은 어휘 없이 돌았다"를 요청에서 바로 읽게 한다.
        if (settings != null) {
            request.settings(settings);
        }

        try {
            transcribeClient.startTranscriptionJob(request.build());
        } catch (ConflictException e) {
            /*
             * 같은 잡 이름이 이미 있다. 잡 이름에 retryCount 가 들어 있고 전이가 CAS 로 막혀
             * 있으므로(SttBlockRepository#markQueuedForRetry) 정상 경로에서는 나오지 않는다 —
             * 나왔다면 **우리 상태와 제공자 상태가 어긋난 것**이다(제출은 됐는데 응답을 못 받아
             * 우리 쪽이 실패로 처리한 경우가 대표적이다).
             *
             * 성공으로 삼키지 않는다. 삼키면 그 블록은 QUEUED 로 남는데 우리가 만든 잡이 아니라
             * 결과를 되짚을 이름도 확신할 수 없다. 사람이 다시 눌러 새 이름으로 제출하게 한다.
             */
            log.error("STT 잡 이름이 이미 있다 — 우리 상태와 제공자 상태가 어긋났다. "
                    + "meetingId={} blockSeq={} job={}", job.meetingId(), job.blockSeq(),
                    job.providerJobName(), e);
            throw new BusinessException(CaptureErrorCode.STT_SUBMIT_FAILED);
        } catch (TranscribeException e) {
            // 제출 실패는 삼키면 안 된다(포트 주석) — 화면은 "재처리를 시작했습니다"라고 말하는데
            // 아무 일도 일어나지 않고, 그 블록은 QUEUED 로 영원히 남아 다시 누를 수도 없다.
            log.error("STT 제출 실패 — meetingId={} blockSeq={} job={} s3Key={}",
                    job.meetingId(), job.blockSeq(), job.providerJobName(), job.audioS3Key(), e);
            throw new BusinessException(CaptureErrorCode.STT_SUBMIT_FAILED);
        }

        log.info("STT 제출 — meetingId={} blockSeq={} job={} s3Key={} 구간={}~{}ms 어휘={} 결과키={}",
                job.meetingId(), job.blockSeq(), job.providerJobName(), job.audioS3Key(),
                job.startOffsetMs(), job.endOffsetMs(),
                settings == null ? "없음" : settings.vocabularyName(), outputKey);
    }

    /*
     * 어휘는 **READY 일 때만** 붙인다.
     *
     * PENDING 인 이름을 보내면 Transcribe 가 BadRequest 로 거절해 제출 자체가 실패한다 —
     * 그런데 명세는 "READY 가 아니어도 녹음은 시작할 수 있다"이고, 그 뜻은 고유명사 인식률만
     * 낮아진다는 것이다. 어휘가 늦게 만들어졌다는 이유로 받아쓰기가 통째로 실패하면 그 계약이
     * 깨진다.
     *
     * 조회 실패도 같은 방향으로 간다 — 어휘를 못 읽었다고 제출을 멈추지 않는다.
     */
    private Settings settingsFor(long meetingId) {
        Optional<VocabularyView> vocabulary;
        try {
            vocabulary = vocabularyRepository.findByMeeting(meetingId);
        } catch (RuntimeException e) {
            log.warn("커스텀 어휘 조회 실패 — 어휘 없이 제출한다. meetingId={}", meetingId, e);
            return null;
        }

        return vocabulary
                .filter(view -> view.status() == VocabularyStatus.READY)
                .filter(view -> view.providerVocabularyName() != null
                        && !view.providerVocabularyName().isBlank())
                .map(view -> Settings.builder()
                        // ⚠ ShowSpeakerLabels 를 켜지 않는다(클래스 주석 — 설계 결정이다).
                        .vocabularyName(view.providerVocabularyName())
                        .build())
                .orElse(null);
    }

    /*
     * 결과 키를 오디오 키에서 만든다 — 접두사를 바꾸고 확장자를 json 으로 바꾼다.
     *
     *   stt-temp/org-1/meeting-500/blocks/3.wav  →  stt-out/org-1/meeting-500/blocks/3.json
     *
     * <h2>왜 오디오 키에서 파생시키나</h2>
     * SttJob 에 companyId 가 없다. 포트에 넣을 수도 있지만, 결과 위치는 **오디오가 어디 있는지에
     * 딸린 값**이라 그 경로를 그대로 따라가는 편이 낫다 — 조직 구분이 오디오 키에 이미 들어 있고,
     * 두 곳에서 각각 조립하면 언젠가 한쪽만 바뀐다.
     *
     * 접두사가 다르면(예상과 다른 오디오 경로) 그대로 뒤에 붙인다. 조용히 다른 규칙으로 만드는
     * 것보다 예측 가능한 자리에 쌓이는 편이 찾기 쉽다.
     */
    private String outputKeyOf(String audioS3Key) {
        String withoutPrefix = audioS3Key.startsWith(AUDIO_PREFIX)
                ? audioS3Key.substring(AUDIO_PREFIX.length())
                : audioS3Key;
        String withoutExtension = withoutPrefix.endsWith(".wav")
                ? withoutPrefix.substring(0, withoutPrefix.length() - ".wav".length())
                : withoutPrefix;
        return outputPrefix + withoutExtension + ".json";
    }

    /* 블록 오디오가 쌓이는 접두사(cap 의 조립 어댑터가 정한 경로다). */
    private static final String AUDIO_PREFIX = "stt-temp/";

    /*
     * 확장자로 포맷을 정한다. 모르면 null — 호출자가 필드를 빼 제공자 판정에 맡긴다.
     *
     * 대소문자를 가리지 않는다. 사용자가 올리는 파일은 ".WAV" 로 오기도 하고, 그걸 모르는
     * 확장자로 취급하면 정상 파일이 매번 판정 없이 나간다.
     */
    private static MediaFormat mediaFormatOf(String s3Key) {
        if (s3Key == null) {
            return null;
        }
        int dot = s3Key.lastIndexOf('.');
        if (dot < 0 || dot == s3Key.length() - 1) {
            return null;
        }
        return FORMAT_BY_EXTENSION.get(s3Key.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
    }
}
