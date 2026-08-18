package com.module06.backend.capture.infrastructure.stt;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.ConflictException;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.Settings;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscribeException;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJob;

import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.port.out.SttJobPort;
import com.module06.backend.capture.application.service.MeetingAttendeeCountProvider;
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
 * <h2>화자 분리(diarization)를 켠다 — 2026-08-17 결정을 뒤집었다</h2>
 * 예전 주석은 여기서 ShowSpeakerLabels 를 **끄는** 것이 설계 결정이라고 적고 있었다. 근거는
 * "이 제품은 화자를 기기 문제로 바꿔 푼다 — 참석자 각자 브라우저가 person_id 와 rms 를 실어
 * 보내고 L1 이 음량이 가장 큰 사람을 고른다"였다.
 *
 * <b>그 근거가 사라졌다.</b> 녹음이 host 한 대로 확정되면서 자막도 host 만 보내게 됐고
 * (CaptionController host-only), L1 의 「전원 자막」 게이트는 참석자가 2명 이상인 모든 회의에서
 * 거짓이 된다 — 즉 <b>모든 발화의 화자가 NULL 이었다.</b> 자막을 아무리 쌓아도 바뀌지 않는다.
 * 게이트를 완화하는 방향은 막혀 있다(host 만 자막을 켠 회의에서 남의 발화가 전부 host 것이
 * 된다). 바꿀 수 있는 결정은 이것 하나였다.
 *
 * <h2>예전 반대 사유 둘 중 하나는 여전히 유효하다 — 그래서 주 경로로 올린다</h2>
 * "믿지 않기로 한 신호가 섞이면 화자 판정이 두 갈래가 되고, 둘이 다를 때 어느 쪽이 맞는지
 * 판단할 근거가 없다"는 지적은 옳다. 그래서 <b>보조로 붙이지 않는다.</b> 라벨이 있는 발화는
 * 라벨 경로만, 없는 발화는 rms 경로만 본다 — 둘은 라벨의 유무로 배타적이라 한 발화를 두고
 * 다투지 않는다(SpeakerAttributionResolver 주석).
 *
 * 나머지 하나("요금이 오른다")는 <b>근거를 확인하지 못했다.</b> 뒤집을 때 요금표를 다시 보지
 * 않았고, 이 주석은 그 값을 아는 척하지 않는다. 실제로 오른다면 그건 이 결정을 되돌릴 사유가
 * 아니라 상한(MaxSpeakerLabels)과 대상 블록을 좁힐 사유다 — 지금 대안은 화자를 영영 모르는
 * 것이고, 그쪽은 담당자 배정이 통째로 성립하지 않는다.
 *
 * <h2>라벨은 사람이 아니다. 여기서는 붙이기만 한다</h2>
 * 돌아오는 값은 {@code spk_0}·{@code spk_1} 이고 누구인지는 말하지 않는다. 사람으로 바꾸는 것은
 * 별도 판정이고(SpeakerLabelAnchorResolver), 이 어댑터는 그 입력을 만들 뿐이다.
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

    /*
     * 화자 수 상한의 허용 범위. Transcribe 가 이 밖의 값을 거절하므로 우리 값을 여기에 가둔다.
     *
     * **가두는 것이지 판단하는 것이 아니다.** 참석자가 40명인 회의에서 30 을 보내면 라벨이
     * 부족해 여러 사람이 한 라벨로 합쳐지는데, 그건 여기서 고칠 수 있는 문제가 아니다 —
     * 대신 그런 회의는 앵커가 붙지 않아 판정을 포기한다(합쳐진 라벨에 닻을 내리면 남의 발화가
     * 확정으로 남의 것이 된다). 상한을 넘겼다는 사실은 아래에서 로그로 남긴다.
     */
    private static final int MIN_SPEAKER_LABELS = 2;
    private static final int MAX_SPEAKER_LABELS = 30;

    /*
     * 참석자 수를 못 읽었을 때 쓰는 상한.
     *
     * <h2>왜 지어내는가 — 여기서는 모르는 것이 기권의 근거가 아니다</h2>
     * 이 코드베이스의 기본은 "모르면 기권"이다. 그런데 그 규칙은 **틀린 값을 쓰면 남에게 일이
     * 배정되는** 자리를 위한 것이고, 여기는 그 자리가 아니다. 상한을 안 주면 화자 분리 자체가
     * 꺼지고 그 회의는 화자가 확정적으로 NULL 이 된다 — 기권이 안전한 쪽이 아니라 유일한
     * 실패다.
     *
     * 넉넉히 잡는 쪽으로 틀린다. 넘치면 한 사람이 여러 라벨로 쪼개져 닻이 안 붙는 라벨이
     * 남을 뿐이고(그 발화는 화자가 NULL 로 남는다), 모자라면 두 사람이 한 라벨로 합쳐져
     * **오귀속이 확정으로 저장된다.** 두 방향의 실패 비용이 다르다.
     */
    private static final int DEFAULT_MAX_SPEAKER_LABELS = 10;

    /*
     * 점유된 이름을 포기하고 새로 지을 때 붙이는 꼬리 — `…-r0-x3f9a1c72`.
     *
     * 표식(`-x`)을 두는 이유는 <b>로그와 콘솔에서 이 이름이 정상 규칙이 아님이 바로 보여야</b>
     * 하기 때문이다. 잡 이름만 놓고 blockSeq·retryCount 를 되짚는 사람이 있고(운영 조회가 그렇게
     * 한다), 난수만 덧붙이면 그 값이 규칙의 일부인지 아닌지 구분되지 않는다.
     *
     * 길이는 8 이다. 이름 전체 상한 200 안에서 네임스페이스(최대 64+1)와 본체(최악 56)를 빼도
     * 남고, 충돌 회피에 필요한 것은 유일성이지 128비트가 아니다.
     */
    private static final String FRESH_NAME_MARKER = "-x";
    private static final int FRESH_NAME_LENGTH = 8;

    private final TranscribeClient transcribeClient;
    private final MeetingVocabularyRepository vocabularyRepository;
    private final MeetingAttendeeCountProvider attendeeCountProvider;
    private final String bucket;
    private final String outputPrefix;

    public SttTranscribeJobAdapter(TranscribeClient transcribeClient,
                                   MeetingVocabularyRepository vocabularyRepository,
                                   MeetingAttendeeCountProvider attendeeCountProvider,
                                   SttTranscribeProperties properties) {
        this.transcribeClient = transcribeClient;
        this.vocabularyRepository = vocabularyRepository;
        this.attendeeCountProvider = attendeeCountProvider;
        this.bucket = properties.bucket();
        this.outputPrefix = properties.outputPrefix();
    }

    @Override
    public String submit(SttJob job) {
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

        // 이름이 충돌했을 때 "이미 있는 그 잡이 이 녹음인지"를 판정하는 근거가 되므로 변수로 둔다.
        String mediaFileUri = "s3://" + bucket + "/" + job.audioS3Key();
        Settings settings = settingsFor(job.meetingId());

        try {
            startJob(job, job.providerJobName(), mediaFileUri, settings);
            return job.providerJobName();
        } catch (ConflictException e) {
            /*
             * 같은 잡 이름이 이미 있다. 잡 이름에 retryCount 가 들어 있고 전이가 CAS 로 막혀
             * 있으므로(SttBlockRepository#markQueuedForRetry) 정상 경로에서는 나오지 않는다 —
             * 나왔다면 **우리 상태와 제공자 상태가 어긋난 것**이다.
             *
             * 예전에는 여기서 무조건 실패로 올렸다. "결과를 되짚을 이름을 확신할 수 없다"가
             * 근거였는데, **물어보면 확신할 수 있다** — 같은 오디오면 채택하고, 다른 오디오면
             * 그 이름을 포기하고 새 이름으로 간다.
             */
            if (pointsToSameAudio(job, mediaFileUri, e)) {
                return job.providerJobName();
            }
            return submitUnderFreshName(job, mediaFileUri, settings);
        } catch (TranscribeException e) {
            // 제출 실패는 삼키면 안 된다(포트 주석) — 화면은 "재처리를 시작했습니다"라고 말하는데
            // 아무 일도 일어나지 않고, 그 블록은 QUEUED 로 영원히 남아 다시 누를 수도 없다.
            log.error("STT 제출 실패 — meetingId={} blockSeq={} job={} s3Key={}",
                    job.meetingId(), job.blockSeq(), job.providerJobName(), job.audioS3Key(), e);
            throw new BusinessException(CaptureErrorCode.STT_SUBMIT_FAILED);
        }
    }

    /* 잡 하나를 이 이름으로 접수시킨다. 결과 키가 이름에서 나오므로 이름이 바뀌면 여기 전부가 바뀐다. */
    private void startJob(SttJob job, String jobName, String mediaFileUri, Settings settings) {
        String outputKey = outputKeyOf(jobName);

        StartTranscriptionJobRequest.Builder request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode(LANGUAGE)
                .media(Media.builder().mediaFileUri(mediaFileUri).build())
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

        // 화자 분리를 항상 켜므로 Settings 는 언제나 붙는다. 예전에는 어휘가 READY 가 아니면
        // Settings 자체를 뺐는데(그래야 "어휘 없이 돌았다"가 요청에서 바로 읽혔다), 이제 그
        // 구분은 vocabularyName 이 비었는지로 읽는다.
        request.settings(settings);

        transcribeClient.startTranscriptionJob(request.build());

        log.info("STT 제출 — meetingId={} blockSeq={} job={} s3Key={} 구간={}~{}ms 어휘={} 화자상한={} 결과키={}",
                job.meetingId(), job.blockSeq(), jobName, job.audioS3Key(),
                job.startOffsetMs(), job.endOffsetMs(),
                settings.vocabularyName() == null ? "없음" : settings.vocabularyName(),
                settings.maxSpeakerLabels(), outputKey);
    }

    /*
     * 그 이름은 남이 쓰고 있다 — **포기하고 새 이름으로 제출한다.**
     *
     * <h2>왜 필요한가 — 이름 하나가 회의를 영구히 막는다 (2026-08-18 meeting-2·3)</h2>
     * 결정적 이름은 <b>다시 계산해도 같은 값</b>이라, 그 이름이 남에게 점유당하면 재시도가 몇 번
     * 돌든 같은 벽에 부딪힌다. LostSttTriggerRecoveryService 가 5분마다 다시 걸지만 이름을 못 바꿔
     * 24시간 상한까지 실패만 반복했다 — 그 회의는 발화 0건이라 분석도 영영 시작되지 않는다.
     *
     * 관측된 점유자는 두 종류였고 <b>둘 다 회의 ID 재사용이 아니어도 생긴다.</b>
     *
     * <ul>
     *   <li><b>같은 회의의 다른 제출 경로</b>(meeting-2) — 실시간 캡처의 자동 절단 블록은
     *       {@code stt-temp/…/blocks/0.wav} 를, 통파일 경로(CAP-10·비대면 녹음 등록)는 업로드 원본
     *       {@code recordings/…/*.m4a} 를 보내는데 <b>둘 다 blockSeq 0</b> 이라 이름이 같다.
     *       한 회의에서 두 경로가 모두 도는 순간 뒤에 온 쪽이 영구히 막힌다.</li>
     *   <li><b>다른 세대·다른 조직의 잡</b>(meeting-3 은 org-17 골드 픽스처와 부딪혔다) — 잡 이름
     *       네임스페이스는 AWS 계정 단위라 DB 보다 오래 산다(SttJobNameFactory 주석).</li>
     * </ul>
     *
     * <h2>왜 retryCount 를 올리지 않는가</h2>
     * {@code -r1} 로 올리는 방법이 먼저 떠오르지만 두 가지가 걸린다. 그 값은 <b>사람이 몇 번
     * 재처리했는지</b>를 세는 값이라(STT-04 응답에 그대로 나간다) 충돌 회피로 소모하면 화면의
     * 숫자가 거짓이 되고, 애초에 {@code -r1} 도 <b>같은 이유로 이미 점유돼 있을 수 있다</b> —
     * 결정적인 값을 하나 더 시도하는 것은 같은 벽을 한 칸 옆에서 다시 만나는 것이다.
     *
     * <h2>왜 처음부터 UUID 를 안 넣는가</h2>
     * 이름이 매번 달라지면 충돌이 사라지는 대신 <b>채택(pointsToSameAudio)도 같이 사라진다</b> —
     * 제출은 도달했는데 응답을 못 받은 경우, 이름을 되짚을 수 없어 이미 끝난 전사를 버리고 같은
     * 오디오에 요금을 두 번 낸다. 그래서 <b>첫 시도는 결정적으로 두고, 막혔을 때만</b> 유일한
     * 값을 붙인다. 이 이름은 stt_block 에 저장되므로(호출자가 고친다) 폴링이 되짚을 수 있다.
     */
    private String submitUnderFreshName(SttJob job, String mediaFileUri, Settings settings) {
        String freshName = job.providerJobName() + FRESH_NAME_MARKER
                + UUID.randomUUID().toString().replace("-", "").substring(0, FRESH_NAME_LENGTH);

        try {
            startJob(job, freshName, mediaFileUri, settings);
        } catch (TranscribeException e) {
            /*
             * 새 이름까지 거절당했다. 여기서 또 다른 이름을 지어 도는 것은 실패의 원인이 이름이
             * 아니라는 뜻이므로(권한·버킷·포맷) 무의미하다 — 올린다.
             */
            log.error("새 잡 이름으로도 제출하지 못했다 — meetingId={} blockSeq={} 원래이름={} 새이름={}",
                    job.meetingId(), job.blockSeq(), job.providerJobName(), freshName, e);
            throw new BusinessException(CaptureErrorCode.STT_SUBMIT_FAILED);
        }

        /*
         * WARN 으로 남긴다. 제출은 됐지만 **이름 규칙이 깨진 자리**이고, 이 로그의 빈도가
         * 근본 원인(두 경로가 같은 blockSeq 0 을 쓴다)을 고칠지 판단하는 유일한 근거다.
         */
        log.warn("잡 이름이 다른 오디오에 점유돼 새 이름으로 제출했다 — meetingId={} blockSeq={} "
                        + "원래이름={} 새이름={} 오디오={}",
                job.meetingId(), job.blockSeq(), job.providerJobName(), freshName, job.audioS3Key());
        return freshName;
    }

    /*
     * 이름이 충돌했을 때, **이미 있는 그 잡이 이 녹음인지 물어보고 같으면 우리 것으로 채택한다.**
     *
     * <h2>왜 필요한가 — 재제출이 확정적으로 실패하는 상태가 있다 (2026-08-18 meeting-2)</h2>
     * SttBlockCreationService 는 INSERT → submit 순서에 REQUIRES_NEW 다. 그래서 <b>제출이 AWS 에
     * 도달했는데 응답을 못 받으면</b>(타임아웃·커넥션 끊김) 우리 행은 통째로 롤백되고 잡만 계속
     * 돌아 혼자 완주한다 — AWS 에는 있고 stt_block 에는 없는 상태다. ConflictException 이 났다는
     * 것 자체가 그 증거이기도 하다: 그 이름의 행이 우리에게 있었다면 UNIQUE(provider_job_name)에서
     * INSERT 가 먼저 터져 AWS 까지 가지도 못한다.
     *
     * 그 상태를 LostSttTriggerRecoveryService 가 정확히 주워서(stt_triggered=1 인데 stt_block 0건)
     * <b>같은 결정적 이름으로</b> 다시 제출하는데, 예전 코드에서는 그 재제출이 매 주기 같은
     * 충돌로 실패했다 — 24시간 상한까지 실패만 반복하고 조용히 포기했다. 복구 배치가 확정 실패
     * 루프였다. 채택이 그 루프를 끊는다(배치 쪽에 코드를 더 넣지 않아도 된다 — 그 경로가 결국
     * 이 어댑터를 지난다).
     *
     * <h2>왜 새 이름으로 재제출하지 않는가</h2>
     * 이미 전사가 끝나 있는 경우가 많고(meeting-2 는 충돌 시점에 이미 COMPLETED 였다), 새 이름은
     * <b>같은 오디오를 한 번 더 전사하고 요금을 두 번 낸다.</b> 리포트가 요청한 "기존 작업을 조회해
     * 같은 녹음인지 확인"이 이쪽이다.
     *
     * <h2>같은 녹음인지의 판정은 미디어 URI 하나로 한다</h2>
     * 이름이 같다는 것만으로 채택하면 <b>남의 회의 전사가 이 회의에 붙는다</b> — 재시드로
     * meetingId 가 재사용된 경우가 정확히 그 위험이고(SttJobNameFactory 주석), 그건 이름만으로는
     * 구분되지 않는다. 그래서 우리가 보내려던 s3:// URI 와 <b>정확히 같을 때만</b> 채택한다.
     * 어긋나면 채택하지 않고 예전처럼 실패로 올린다 — 두 URI 를 로그에 남기므로 사람이 비교할 수
     * 있다. 느슨하게 맞추는 쪽(호스트 형식 변환·퍼센트 디코딩)으로 가지 않은 이유는, 여기서
     * 틀리는 방향이 "복구가 한 번 안 된다"가 아니라 <b>"남의 전사를 확정으로 저장한다"</b>라서다.
     * Transcribe 는 우리가 보낸 값을 그대로 돌려주므로 정상 경로에서는 정확히 일치한다.
     *
     * <h2>잡이 FAILED 여도 채택한다</h2>
     * 채택하면 블록은 QUEUED 로 남고 폴링이 그 이름으로 결과를 가져간다(SttJobResultPort#fetch).
     * 잡이 FAILED 면 폴링이 블록을 FAILED 로 닫고, 그때 비로소 사람이 재처리를 눌러 <b>-r1 이라는
     * 새 이름으로</b> 다시 돌릴 수 있다 — 지금까지 없던 복구 경로가 그렇게 열린다.
     *
     * @return 같은 오디오라 채택했으면 true. 다른 오디오면 false — 호출자가 새 이름으로 간다
     */
    private boolean pointsToSameAudio(SttJob job, String expectedMediaFileUri, ConflictException conflict) {
        TranscriptionJob existing;
        try {
            existing = transcribeClient.getTranscriptionJob(GetTranscriptionJobRequest.builder()
                            .transcriptionJobName(job.providerJobName())
                            .build())
                    .transcriptionJob();
        } catch (RuntimeException e) {
            /*
             * 물어보지 못했으면 채택도, 새 이름으로 가지도 않는다 — **올린다.**
             *
             * 확인 없이 채택하면 남의 전사가 붙는다. 반대로 확인 없이 새 이름으로 가면, 실은
             * 우리 잡이었을 때 이미 끝난 전사를 버리고 같은 오디오에 요금을 두 번 낸다. 조회
             * 실패는 대개 스로틀·네트워크라 다음 주기(복구 배치 5분)에 답이 오므로, 그때 판정하는
             * 편이 두 손실을 모두 피한다. 영구적으로 못 읽는 상태라면 그건 권한 문제이고
             * (transcribe:GetTranscriptionJob), 이 로그가 그 사실을 가리킨다.
             */
            log.error("이미 있는 STT 잡을 조회하지 못했다 — 채택도 재제출도 하지 않는다. "
                            + "meetingId={} blockSeq={} job={}",
                    job.meetingId(), job.blockSeq(), job.providerJobName(), e);
            throw new BusinessException(CaptureErrorCode.STT_SUBMIT_FAILED);
        }

        String existingMediaFileUri = existing == null || existing.media() == null
                ? null
                : existing.media().mediaFileUri();
        if (!expectedMediaFileUri.equals(existingMediaFileUri)) {
            /*
             * 남이 쓰는 이름이다. **여기서 끝내지 않는다** — 예전에는 실패로 올렸고, 그래서
             * 이름이 결정적인 만큼 그 회의가 영구히 막혔다(2026-08-18 meeting-2·3).
             * 호출자가 새 이름으로 간다(submitUnderFreshName).
             */
            log.warn("이미 있는 STT 잡이 다른 오디오를 가리킨다 — 이 이름은 쓸 수 없다. "
                            + "meetingId={} blockSeq={} job={} 기존오디오={} 우리오디오={}",
                    job.meetingId(), job.blockSeq(), job.providerJobName(),
                    existingMediaFileUri, expectedMediaFileUri, conflict);
            return false;
        }

        /*
         * WARN 으로 남긴다. 채택은 성공이지만 **정상은 아니다** — 제출 응답이 유실됐다는 신호이고,
         * 조용히 고치면 그 유실이 얼마나 자주 일어나는지 아무도 모른다(LostSttTriggerRecoveryService
         * 가 복구 성공을 INFO 로 남기는 것과 같은 이유).
         */
        log.warn("이미 있는 STT 잡을 채택했다 — 같은 오디오다(제출은 도달했는데 응답을 못 받았던 것이다). "
                        + "meetingId={} blockSeq={} job={} 제공자상태={} 오디오={}",
                job.meetingId(), job.blockSeq(), job.providerJobName(),
                existing.transcriptionJobStatusAsString(), expectedMediaFileUri);
        return true;
    }

    /*
     * 이 잡의 Settings — 화자 분리는 **항상**, 어휘는 READY 일 때만.
     *
     * <h2>어휘는 READY 일 때만 붙인다</h2>
     * PENDING 인 이름을 보내면 Transcribe 가 BadRequest 로 거절해 제출 자체가 실패한다 —
     * 그런데 명세는 "READY 가 아니어도 녹음은 시작할 수 있다"이고, 그 뜻은 고유명사 인식률만
     * 낮아진다는 것이다. 어휘가 늦게 만들어졌다는 이유로 받아쓰기가 통째로 실패하면 그 계약이
     * 깨진다. 조회 실패도 같은 방향으로 간다 — 어휘를 못 읽었다고 제출을 멈추지 않는다.
     *
     * <h2>화자 분리는 어휘 상태와 무관하다</h2>
     * 예전에는 어휘가 READY 일 때만 Settings 를 만들었다. 그 구조를 그대로 두고 화자 분리를
     * 얹으면 **어휘가 없는 회의에서 화자 분리도 함께 꺼진다** — 둘은 아무 관계도 없는데
     * 한쪽의 실패가 다른 쪽을 조용히 끄는 배선이 된다. 그래서 빌더를 먼저 세우고 어휘를
     * 조건부로 얹는 순서로 뒤집었다.
     */
    private Settings settingsFor(long meetingId) {
        Settings.Builder settings = Settings.builder()
                .showSpeakerLabels(true)
                .maxSpeakerLabels(maxSpeakerLabelsOf(meetingId));

        Optional<VocabularyView> vocabulary;
        try {
            vocabulary = vocabularyRepository.findByMeeting(meetingId);
        } catch (RuntimeException e) {
            log.warn("커스텀 어휘 조회 실패 — 어휘 없이 제출한다. meetingId={}", meetingId, e);
            return settings.build();
        }

        vocabulary
                .filter(view -> view.status() == VocabularyStatus.READY)
                .filter(view -> view.providerVocabularyName() != null
                        && !view.providerVocabularyName().isBlank())
                .ifPresent(view -> settings.vocabularyName(view.providerVocabularyName()));

        return settings.build();
    }

    /*
     * 화자 수 상한. 참석자 명단 그대로를 주고, 못 읽으면 넉넉한 기본값을 준다.
     *
     * 상한 밖으로 나가는 두 경우를 **로그로 남긴다.** 참석자가 1명인 회의(온라인 1:1 이
     * 아니라 명단이 덜 채워진 경우가 대부분이다)와 30명을 넘는 회의는 화자 판정이 구조적으로
     * 나빠지는데, 그 사실이 여기 말고는 드러날 곳이 없다 — 나중에 "이 회의만 화자가 왜 다
     * 비었지"를 되짚을 유일한 단서다.
     */
    private int maxSpeakerLabelsOf(long meetingId) {
        OptionalInt attendees;
        try {
            attendees = attendeeCountProvider.attendeeCountOf(meetingId);
        } catch (RuntimeException e) {
            log.warn("참석자 수 조회 실패 — 화자 상한 기본값 {} 로 제출한다. meetingId={}",
                    DEFAULT_MAX_SPEAKER_LABELS, meetingId, e);
            return DEFAULT_MAX_SPEAKER_LABELS;
        }

        if (attendees.isEmpty()) {
            log.warn("참석자 명단이 비어 화자 상한을 기본값 {} 로 둔다. meetingId={}",
                    DEFAULT_MAX_SPEAKER_LABELS, meetingId);
            return DEFAULT_MAX_SPEAKER_LABELS;
        }

        int count = attendees.getAsInt();
        if (count < MIN_SPEAKER_LABELS) {
            // 명단이 1명이다. 화자 분리를 끄지는 않는다 — 명단이 덜 채워졌을 뿐 실제로는
            // 여러 사람이 말한 회의일 수 있고, 그때 끄면 라벨이 통째로 사라진다.
            log.info("참석자가 {}명이라 화자 상한을 최소값 {} 로 올린다. meetingId={}",
                    count, MIN_SPEAKER_LABELS, meetingId);
            return MIN_SPEAKER_LABELS;
        }
        if (count > MAX_SPEAKER_LABELS) {
            // 상한을 넘겼다. 여러 사람이 한 라벨로 합쳐질 수 있고, 그런 라벨에는 앵커가
            // 붙지 않아 그 발화들의 화자가 NULL 로 남는다(오귀속보다 나은 방향이다).
            log.warn("참석자 {}명이 화자 상한 {}을 넘겨 라벨이 합쳐질 수 있다. meetingId={}",
                    count, MAX_SPEAKER_LABELS, meetingId);
            return MAX_SPEAKER_LABELS;
        }
        return count;
    }

    /*
     * 결과 키를 **잡 이름**으로 만든다.
     *
     *   meeting-500-block-3-r0  →  stt-out/meeting-500-block-3-r0.json
     *
     * <h2>⚠ 오디오 키에서 파생시키면 안 된다 (2026-08-15 운영 정지)</h2>
     * 예전에는 오디오 키의 접두사를 바꾸고 확장자를 json 으로 바꿨다. 자동 녹음 경로에서는 키가
     * {@code stt-temp/org-1/meeting-500/blocks/3.wav} 처럼 **서버가 만든 이름**이라 문제가 없었다.
     *
     * 그런데 수동·온라인 회의 업로드는 **사용자 파일명이 키에 그대로 들어간다.** 그리고
     * Transcribe 의 outputKey 는 아래 문자만 허용한다 —
     *
     *     [a-zA-Z0-9-_.!*'()/&$@=;:+,? \x00-\x1F\x7F]{1,1024}
     *
     * **한글이 없다.** `음성 260814_124512.m4a` 를 올리면 제출이 400 으로 거절되고, 그 실패는
     * cap 의 best-effort 트리거가 삼켜 recording 만 남는다(stt_triggered=1 인데 stt_block 0건).
     * 화면에는 "제출 완료"로 보이고 요약은 영원히 안 나온다. 2026-08-15 에 회의 다섯 건이
     * 그렇게 멈췄다.
     *
     * 공백·괄호는 저 목록에 있어서 통과한다 — 그래서 `videoplayback (1).m4a` 는 제출까지는 됐다.
     * 대신 그건 결과를 읽을 때 퍼센트 디코딩에서 터졌다(SttTranscribeResultAdapter 주석).
     * **같은 파일명 하나가 두 곳을 다르게 부순다.**
     *
     * <h2>왜 잡 이름인가</h2>
     * 이미 계정 안에서 유일하고(retryCount 포함) 우리가 만든 값이라 문자 집합이 보장된다.
     * 사용자 입력이 한 글자도 섞이지 않는 유일한 후보다.
     *
     * 조직·회의 구분이 경로에서 사라지지만 잡 이름이 {@code meeting-{id}-block-{seq}-r{retry}} 라
     * 그 정보를 그대로 들고 있다. 그리고 결과를 **읽을 때는 이 값을 쓰지 않는다** — Transcribe 가
     * 알려준 transcriptFileUri 를 그대로 따라가므로, 여기를 바꿔도 읽기 경로는 영향이 없다.
     *
     * ⚠ 근본 차단은 여전히 **업로드 시점에 파일명을 정규화**하는 것이다(#535). 여기서 막는 것은
     * 제출 실패 하나뿐이고, 사용자 파일명이 키에 들어가는 한 다른 자리에서 또 나온다.
     */
    private String outputKeyOf(String providerJobName) {
        return outputPrefix + providerJobName + ".json";
    }

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
