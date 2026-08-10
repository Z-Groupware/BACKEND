package com.module06.backend.cap.infrastructure.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.module06.backend.cap.application.port.out.SttBlockCutDetectionPort;
import com.module06.backend.cap.infrastructure.storage.CapS3Properties;

/*
 * AI-01(VAD 절단점 계산) 실 어댑터 — POST /internal/vad/cutpoint.
 *
 * <h2>AI-02~07과 같은 RestClient를 재사용한다</h2>
 * 같은 AI EC2를 쓰므로 capture.infrastructure.ai.AiLayerClientConfig가 이미 만들어둔
 * aiRestClient 빈(base-url·X-Internal-Token·타임아웃 전부 거기서 검증됨)을 그대로 주입받는다 —
 * 인증 설정을 여기서 새로 만들면 두 곳 중 하나가 나중에 바뀌었을 때 어긋난다.
 *
 * <h2>bucket을 요청에 넣지만 포트 계약에는 없다</h2>
 * AI-01 요청 스키마(CutpointRequest)는 bucket이 필수지만, "어느 S3 버킷을 쓰는지"는 cap
 * 내부의 순수 인프라 정보다 — 오케스트레이션(SttBlockCutTrigger)이 몰라도 되는 값이라
 * 포트 시그니처에 넣지 않고 이 어댑터가 CapS3Properties로 직접 안다.
 *
 * <h2>minSilenceMs를 안 보낸다</h2>
 * Python 쪽 기본값(DEFAULT_MIN_SILENCE_MS)을 그대로 쓴다. 여기서 상수로 박으면 값을 조정할
 * 때마다 Spring도 같이 배포해야 한다(schemas/vad.py 주석과 같은 이유).
 *
 * <h2>실패를 세분화하지 않는다</h2>
 * AiLayerHttpAdapter(AI-02~07)는 재시도 가능/불가능을 나눠 계층별로 다르게 처리하지만,
 * 여기 호출자(SttBlockCutTrigger)는 어차피 모든 실패를 best-effort로 삼켜 다음 청크가
 * 재시도하게 둔다 — 그 이상의 분류가 필요 없다.
 */
@Component
@Profile("prod")
public class SttBlockCutDetectionHttpAdapter implements SttBlockCutDetectionPort {

    private static final String PATH = "/internal/vad/cutpoint";

    private final RestClient aiRestClient;
    private final String bucket;

    public SttBlockCutDetectionHttpAdapter(RestClient aiRestClient, CapS3Properties capS3Properties) {
        this.aiRestClient = aiRestClient;
        this.bucket = capS3Properties.bucket();
    }

    @Override
    public CutDetectionResult detectCutPoint(long meetingId, String windowAudioS3Key, long windowStartOffsetMs,
                                             long targetOffsetMs) {
        try {
            CutpointResponseDto response = aiRestClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CutpointRequestDto(meetingId, bucket, windowAudioS3Key, windowStartOffsetMs,
                            targetOffsetMs))
                    .retrieve()
                    .body(CutpointResponseDto.class);

            if (response == null) {
                throw new IllegalStateException(PATH + " 응답 본문이 비어 있습니다.");
            }
            return new CutDetectionResult(response.cutOffsetMs(), response.cutReason(), response.silenceMs());
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException(PATH + " 호출 실패: " + e.getMessage(), e);
        }
    }

    // AI-01 요청(schemas/vad.py CutpointRequest) — CamelModel이라 필드명 그대로 camelCase JSON.
    private record CutpointRequestDto(
            long meetingId,
            String bucket,
            String s3Key,
            long windowStartOffsetMs,
            long targetOffsetMs
    ) {
    }

    // AI-01 응답(schemas/vad.py CutpointResponse). silenceMs는 FALLBACK_OVERLAP일 때 null.
    private record CutpointResponseDto(
            long cutOffsetMs,
            String cutReason,
            Long silenceMs
    ) {
    }
}
