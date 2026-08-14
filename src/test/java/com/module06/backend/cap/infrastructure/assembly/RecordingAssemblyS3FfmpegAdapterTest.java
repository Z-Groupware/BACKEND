package com.module06.backend.cap.infrastructure.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.infrastructure.storage.CapS3Properties;

/*
 * 디스크 용량 가드(requireSufficientDiskSpace/estimateRequiredBytes, private)만 단위 테스트한다 —
 * ffmpeg를 이 개발 환경에서 실행할 수 없어(SttBlockAudioAssemblyS3FfmpegAdapterTest와 동일한 이유)
 * 나머지 파이프라인은 검증 불가하다.
 *
 * estimateRequiredBytes는 순수 계산이라 리플렉션으로 직접 검증하고, requireSufficientDiskSpace는
 * 실제 Files.getFileStore 호출이 섞여 있어 "가용 공간을 초과하는" 경계는 검증하지 않는다 —
 * RecordingPart가 청크 하나를 2MB로 제한해(MAX_SIZE_BYTES) 실제 디스크보다 큰 값을 파트로 만들 수
 * 없다. 대신 정상 범위에서 예외가 안 나는지만 스모크 테스트한다.
 */
@DisplayName("RecordingAssemblyS3FfmpegAdapter 디스크 용량 가드")
class RecordingAssemblyS3FfmpegAdapterTest {

    private final RecordingAssemblyS3FfmpegAdapter adapter =
            new RecordingAssemblyS3FfmpegAdapter(null, null, null, null, null, null, null, null,
                    new CapS3Properties("test-bucket"));

    /* 추정치가 (원본 총합 × 3배) + 500MB 고정 여유분인지 검증한다. */
    @Test
    @DisplayName("추정치는 원본 총합의 3배 + 500MB 고정 여유분이다")
    void estimatesThreeTimesTotalPlusFixedMargin() {
        List<RecordingPart> parts = List.of(part(1_000_000L), part(2_000_000L)); // 총합 3,000,000

        long estimated = invokeEstimate(parts);

        long expected = 3_000_000L * 3 + 500L * 1024 * 1024;
        assertThat(estimated).isEqualTo(expected);
    }

    /* 파트가 없으면(0바이트) 고정 여유분만 남는지 검증한다. */
    @Test
    @DisplayName("파트가 없으면 고정 여유분(500MB)만 추정치로 남는다")
    void estimatesFixedMarginOnlyWhenNoParts() {
        long estimated = invokeEstimate(List.of());

        assertThat(estimated).isEqualTo(500L * 1024 * 1024);
    }

    /* 정상적인(작은) 총합이면 이 개발 환경의 임시 디렉터리 여유 공간을 넘지 않아 예외가 안 나는지 검증한다. */
    @Test
    @DisplayName("정상 범위 추정치면 예외 없이 통과한다")
    void passesForNormalSizedMeeting() {
        List<RecordingPart> parts = List.of(part(1_000_000L), part(2_000_000L));

        assertThatCode(() -> invokeRequire(500L, parts)).doesNotThrowAnyException();
    }

    private RecordingPart part(long sizeBytes) {
        return RecordingPart.create(500L, 0, 1, "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm",
                "audio/webm", sizeBytes, 7L);
    }

    private long invokeEstimate(List<RecordingPart> parts) {
        try {
            Method method = RecordingAssemblyS3FfmpegAdapter.class.getDeclaredMethod(
                    "estimateRequiredBytes", List.class);
            method.setAccessible(true);
            return (long) method.invoke(adapter, parts);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void invokeRequire(Long meetingId, List<RecordingPart> parts) throws Exception {
        Method method = RecordingAssemblyS3FfmpegAdapter.class.getDeclaredMethod(
                "requireSufficientDiskSpace", Long.class, List.class);
        method.setAccessible(true);
        method.invoke(adapter, meetingId, parts);
    }
}
