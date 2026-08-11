package com.module06.backend.capture.infrastructure.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
 * AWS Transcribe 제출 설정.
 *
 * <h2>설정이 비면 부팅에서 막는다</h2>
 * SttTranscribeClientConfig 가 @Profile("prod") 라 이 레코드도 prod 에서만 바인딩된다 —
 * 로컬·테스트는 SttJobStubAdapter 가 대신하므로 값이 없어도 영향이 없다. prod 인데 비어 있으면
 * 모든 STT 제출이 실패하므로, 그건 런타임이 아니라 생성 시점에 끊는다
 * (CapS3Properties · AiLayerProperties 와 동일 관용구).
 *
 * <h2>리전·언어는 여기 두지 않는다</h2>
 * 리전은 SttTranscribeClientConfig 가 ap-northeast-2 로 하드코딩한다(이 프로젝트가 쓰는 유일한
 * 리전 · CapS3ClientConfig 와 같은 판단). 언어도 상수다 — 그 이유는 어댑터 주석에 적었다.
 */
@ConfigurationProperties(prefix = "stt.transcribe")
public record SttTranscribeProperties(String bucket, String outputPrefix) {

    public SttTranscribeProperties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("stt.transcribe.bucket 이 비어 있습니다. "
                    + "STT 제출이 전부 실패하므로 부팅을 중단합니다 (SSM /z/prod/S3_BUCKET).");
        }
        if (outputPrefix == null || outputPrefix.isBlank()) {
            throw new IllegalStateException("stt.transcribe.output-prefix 가 비어 있습니다.");
        }
        /*
         * 접두사가 '/' 로 끝나지 않으면 결과 키가 디렉터리 경계를 잃는다 — "stt-out" + "org-1/..."
         * 이 "stt-outorg-1/..." 이 된다. 조용히 붙여주지 않고 막는 이유는, 설정한 사람이 의도한
         * 경로와 실제 경로가 다른 상태로 몇 주가 지날 수 있기 때문이다.
         */
        if (!outputPrefix.endsWith("/")) {
            throw new IllegalStateException(
                    "stt.transcribe.output-prefix 는 '/' 로 끝나야 합니다: " + outputPrefix);
        }
    }
}
