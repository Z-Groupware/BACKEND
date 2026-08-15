package com.module06.backend.capture.infrastructure.stt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.BadRequestException;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.NotFoundException;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJob;

import com.module06.backend.capture.application.port.out.SttJobResultPort;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;

/*
 * SttJobResultPort 의 AWS Transcribe 구현 — 잡 상태 조회 + 결과 JSON 읽기.
 *
 * <h2>단어까지만 펴서 준다</h2>
 * 발화 경계를 정하는 것은 도메인의 몫이다(TranscriptSegmenter). 여기서 문장까지 묶으면 제공자를
 * 바꿀 때 경계 규칙이 함께 바뀌고, 같은 회의의 근거 발화가 다른 것을 가리킨다.
 *
 * <h2>오프셋은 블록 기준 그대로 둔다</h2>
 * Transcribe 는 자기가 받은 오디오의 처음을 0 으로 준다. 회의 기준으로 옮기는 것은 블록의
 * startOffsetMs 를 아는 폴링 서비스가 한다 — 여기서 더하려면 블록 오프셋을 인자로 받아야 하고,
 * 그러면 같은 덧셈이 제공자마다 반복되면서 한 곳이 빠질 자리가 생긴다.
 *
 * <h2>예외를 던지지 않는다</h2>
 * 워커가 블록 하나 때문에 멈추면 밀린 잡 전부가 함께 밀린다. 못 읽은 것은 UNAVAILABLE 로
 * 답하고 상태를 바꾸지 않게 한다 — **못 읽은 것을 실패로 접으면 정상적으로 돌던 잡이 FAILED 로
 * 닫히고, 사람이 재처리를 눌러 같은 구간에 요금이 두 번 나간다.**
 */
@Slf4j
@Component
@Profile("prod")
public class SttTranscribeResultAdapter implements SttJobResultPort {

    /* 화면이 이 코드로 문구를 고른다. 제공자 메시지를 그대로 흘리지 않는다(V5.4 주석). */
    private static final String ERROR_JOB_FAILED = "JOB_FAILED";
    private static final String ERROR_OUTPUT_MISSING = "OUTPUT_MISSING";
    private static final String ERROR_OUTPUT_UNREADABLE = "OUTPUT_UNREADABLE";

    private final TranscribeClient transcribeClient;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public SttTranscribeResultAdapter(TranscribeClient transcribeClient, S3Client s3Client,
                                      ObjectMapper objectMapper) {
        this.transcribeClient = transcribeClient;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    @Override
    public SttJobOutcome fetch(String providerJobName) {
        TranscriptionJob job;
        try {
            job = transcribeClient.getTranscriptionJob(GetTranscriptionJobRequest.builder()
                            .transcriptionJobName(providerJobName)
                            .build())
                    .transcriptionJob();
        } catch (NotFoundException | BadRequestException e) {
            /*
             * 그 이름의 잡이 없다. 제출 응답을 못 받아 우리만 QUEUED 로 남았거나, 보관 기간이
             * 지난 것이다. **실패와 구분해서 답한다** — 서비스가 이 구분으로 다른 판단을 한다.
             *
             * BadRequestException 을 함께 잡는 이유: Transcribe 는 이름 형식이 어긋난 조회에
             * NotFound 가 아니라 BadRequest 로 답한다. 둘 다 "이 이름으로는 결과를 못 찾는다"다.
             */
            log.warn("STT 잡을 찾을 수 없다 — job={}", providerJobName);
            return SttJobOutcome.of(State.UNKNOWN);
        } catch (RuntimeException e) {
            // 네트워크·권한·스로틀. 상태를 바꾸지 않고 다음 주기에 다시 본다.
            log.warn("STT 잡 조회 실패 — job={}", providerJobName, e);
            return SttJobOutcome.of(State.UNAVAILABLE);
        }

        return switch (job.transcriptionJobStatus()) {
            case QUEUED -> SttJobOutcome.of(State.QUEUED);
            case IN_PROGRESS -> SttJobOutcome.of(State.RUNNING);
            case FAILED -> {
                // 제공자 사유는 로그에만 남긴다. 화면에는 우리 코드가 간다.
                log.warn("STT 잡 실패 — job={} 제공자사유={}", providerJobName, job.failureReason());
                yield SttJobOutcome.failed(ERROR_JOB_FAILED);
            }
            case COMPLETED -> completed(providerJobName, job);
            /*
             * SDK 가 모르는 값이다(UNKNOWN_TO_SDK_VERSION) — 또는 null. 새 상태가 생긴 것이므로
             * 실패로 접지 않는다: 그 잡은 아직 돌고 있을 수 있고, 실패로 닫으면 사람이 재처리를
             * 눌러 같은 구간에 요금이 두 번 나간다.
             */
            default -> {
                log.warn("STT 잡 상태를 해석할 수 없다 — job={} status={}",
                        providerJobName, job.transcriptionJobStatusAsString());
                yield SttJobOutcome.of(State.UNAVAILABLE);
            }
        };
    }

    private SttJobOutcome completed(String providerJobName, TranscriptionJob job) {
        String uri = job.transcript() == null ? null : job.transcript().transcriptFileUri();
        if (uri == null || uri.isBlank()) {
            // 완료라는데 결과 위치가 없다. 다시 물어봐도 같을 것이므로 실패로 닫는다.
            log.error("STT 완료인데 결과 위치가 없다 — job={}", providerJobName);
            return SttJobOutcome.failed(ERROR_OUTPUT_MISSING);
        }

        S3Location location;
        try {
            location = S3Location.parse(uri);
        } catch (IllegalArgumentException e) {
            log.error("STT 결과 위치를 해석할 수 없다 — job={} uri={}", providerJobName, uri, e);
            return SttJobOutcome.failed(ERROR_OUTPUT_MISSING);
        }

        byte[] body;
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
            body = object.asByteArray();
        } catch (NoSuchKeyException e) {
            /*
             * 완료라는데 파일이 없다. 라이프사이클 규칙이 지웠거나 다른 버킷을 보고 있는 것이다 —
             * 다시 물어봐도 같으므로 실패로 닫아 재처리 대상이 되게 한다.
             */
            log.error("STT 결과 파일이 없다 — job={} s3://{}/{}", providerJobName,
                    location.bucket(), location.key());
            return SttJobOutcome.failed(ERROR_OUTPUT_MISSING);
        } catch (RuntimeException e) {
            // 읽기 자체가 실패했다. 일시적일 수 있으므로 상태를 바꾸지 않는다.
            log.warn("STT 결과 파일을 읽지 못했다 — job={}", providerJobName, e);
            return SttJobOutcome.of(State.UNAVAILABLE);
        }

        try {
            return SttJobOutcome.completed(parseWords(body));
        } catch (RuntimeException e) {
            /*
             * 파일은 읽혔는데 모양이 다르다. 다시 읽어도 같으므로 실패로 닫는다 — UNAVAILABLE 로
             * 두면 워커가 매 주기 같은 파일을 다시 파싱하며 영원히 돈다.
             */
            log.error("STT 결과를 해석할 수 없다 — job={}", providerJobName, e);
            return SttJobOutcome.failed(ERROR_OUTPUT_UNREADABLE);
        }
    }

    /*
     * Transcribe 결과 JSON 을 단어로 편다.
     *
     * <pre>
     * { "results": { "items": [
     *     { "start_time": "0.0", "end_time": "0.44", "type": "pronunciation",
     *       "alternatives": [ { "content": "안녕하세요" } ] },
     *     { "type": "punctuation", "alternatives": [ { "content": "." } ] } ] } }
     * </pre>
     *
     * <h2>type 을 그대로 믿는다 — 문자로 추측하지 않는다</h2>
     * 부호 여부를 우리가 문자로 판정하면 한국어에서 잘 틀린다(마침표 없이 끝나는 문장이 많고,
     * 숫자·단위 표기에 점이 섞인다). 제공자가 그 구분을 주므로 그것을 쓴다.
     *
     * <h2>시간이 없는 단어는 앞말 끝에 붙인다</h2>
     * 부호 항목에는 start_time·end_time 이 없다. 0 으로 채우면 그 부호가 회의 맨 앞으로
     * 튀어 발화 경계가 무너진다 — 직전 단어의 끝 시각을 쓴다.
     */
    private List<Word> parseWords(byte[] body) {
        JsonNode items = objectMapper.readTree(new String(body, StandardCharsets.UTF_8))
                .path("results").path("items");
        if (!items.isArray()) {
            throw new IllegalStateException("results.items 가 배열이 아니다");
        }

        List<Word> words = new ArrayList<>();
        int lastEndMs = 0;
        for (JsonNode item : items) {
            String content = item.path("alternatives").path(0).path("content").asString(null);
            if (content == null || content.isBlank()) {
                continue;
            }
            boolean punctuation = "punctuation".equals(item.path("type").asString(""));

            int startMs = toMillis(item.path("start_time"), lastEndMs);
            int endMs = toMillis(item.path("end_time"), startMs);
            words.add(new Word(startMs, endMs, content, punctuation));
            lastEndMs = endMs;
        }
        return words;
    }

    /* Transcribe 는 초를 **문자열**로 준다("12.34"). 없으면 기본값을 쓴다(부호 항목). */
    private static int toMillis(JsonNode node, int fallbackMs) {
        String seconds = node.asString(null);
        if (seconds == null || seconds.isBlank()) {
            return fallbackMs;
        }
        try {
            return (int) Math.round(Double.parseDouble(seconds) * 1000);
        } catch (NumberFormatException e) {
            return fallbackMs;
        }
    }

    /*
     * 결과 파일의 위치.
     *
     * transcriptFileUri 는 두 모양으로 온다 — 우리 버킷을 지정했으면
     * {@code https://s3.<region>.amazonaws.com/<bucket>/<key>} 이고, 서비스 관리 버킷이면
     * 만료되는 presigned URL 이다. **우리는 항상 버킷을 지정하므로** 앞의 모양을 기대하고,
     * s3:// 형식도 함께 받아둔다(SDK·리전에 따라 다르게 오는 것을 본 사례가 있다).
     *
     * 쿼리스트링은 잘라낸다 — presigned 형태로 올 경우 서명이 키에 섞인다.
     *
     * <h2>⚠ http(s) 경로는 반드시 퍼센트 디코딩한다</h2>
     * URL 의 경로는 인코딩된 표현이고 S3 오브젝트 키는 **원문**이다. 파일명에 공백·괄호·한글이
     * 있으면 둘이 갈린다 —
     *
     *     URI    …/videoplayback%20%281%29.m4a.json
     *     실제 키 …/videoplayback (1).m4a.json
     *
     * 디코딩하지 않으면 없는 키를 조회하게 되고, 롤에 s3:ListBucket 이 없으면 S3 가 404 를
     * **403 AccessDenied(메시지는 ListBucket)** 로 가려 돌려준다. 그러면 권한 문제로 보이지만
     * 실제로는 키가 어긋난 것이다 — 2026-08-15 운영에서 정확히 그렇게 읽혔다.
     *
     * 파일명이 깨끗하면 인코딩 전후가 같아 통과하므로 **오래 숨어 있다가 사용자가 올린 파일에서
     * 터진다.** 같은 종류가 이미 두 번 있었다(#514 첨부파일 · #516 수동 녹음 presign).
     *
     * URLDecoder 를 쓰지 않는다 — 그건 {@code +} 를 공백으로 바꾸는 폼 인코딩 규칙이라
     * 키에 {@code +} 가 들어 있으면 망가진다. URI#getPath 는 퍼센트 이스케이프만 푼다.
     *
     * s3:// 는 디코딩하지 않는다. 그 표현의 경로는 이미 원문 키다.
     */
    record S3Location(String bucket, String key) {

        static S3Location parse(String uri) {
            String withoutQuery = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;

            if (withoutQuery.startsWith("s3://")) {
                return split(withoutQuery.substring("s3://".length()));
            }
            if (withoutQuery.indexOf("://") < 0) {
                throw new IllegalArgumentException("스킴이 없는 URI: " + uri);
            }

            String path;
            try {
                path = URI.create(withoutQuery).getPath();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("해석할 수 없는 URI: " + uri, e);
            }
            if (path == null || path.isBlank() || path.equals("/")) {
                throw new IllegalArgumentException("경로가 없는 URI: " + uri);
            }
            // path-style: /<bucket>/<key> — 앞의 '/' 를 떼면 split 이 가른다.
            return split(path.substring(1));
        }

        private static S3Location split(String bucketAndKey) {
            int slash = bucketAndKey.indexOf('/');
            if (slash <= 0 || slash == bucketAndKey.length() - 1) {
                throw new IllegalArgumentException("버킷·키를 가를 수 없다: " + bucketAndKey);
            }
            return new S3Location(bucketAndKey.substring(0, slash), bucketAndKey.substring(slash + 1));
        }
    }
}
