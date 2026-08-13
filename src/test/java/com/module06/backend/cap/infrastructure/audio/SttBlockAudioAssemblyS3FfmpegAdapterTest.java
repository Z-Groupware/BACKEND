package com.module06.backend.cap.infrastructure.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.infrastructure.storage.CapS3Properties;

/*
 * requireNoGaps(private) 순수 검증 로직만 단위 테스트한다 — ffmpeg를 이 개발 환경에서 실행할 수
 * 없어(SAMPLE_RATE·concat·트림을 실제로 태우는 나머지 파이프라인은 검증 불가) 남겨뒀던 공백을
 * 메운다. requireNoGaps 자체는 S3Client·RecordingPartRepository를 안 쓰는 순수 함수라, 어댑터를
 * null 의존성으로 생성하고 리플렉션으로 그 메서드만 직접 호출한다(NotificationStreamRegistryTest와
 * 동일한 이 코드베이스의 기존 리플렉션 테스트 패턴).
 */
@DisplayName("SttBlockAudioAssemblyS3FfmpegAdapter.requireNoGaps")
class SttBlockAudioAssemblyS3FfmpegAdapterTest {

    private final SttBlockAudioAssemblyS3FfmpegAdapter adapter =
            new SttBlockAudioAssemblyS3FfmpegAdapter(null, null, new CapS3Properties("test-bucket"));

    /* 요청 구간의 첫~끝 seq가 정확히 이어져 있으면 예외가 나지 않는지 검증한다. */
    @Test
    @DisplayName("빈틈이 없으면(연속 seq) 통과한다")
    void passesWhenNoGap() {
        List<RecordingPart> chunks = chunksOf(1, 2, 3, 4, 5);

        assertThatCode(() -> invokeRequireNoGaps(chunks, 500L, 0, 1, 5)).doesNotThrowAnyException();
    }

    /* 중간 seq 하나가 빠지면(개수 부족) IllegalStateException으로 막는지 검증한다. */
    @Test
    @DisplayName("중간에 빈틈이 있으면 IllegalStateException을 던진다")
    void throwsWhenMiddleSeqMissing() {
        List<RecordingPart> chunks = chunksOf(1, 2, 4, 5); // 3번 없음

        assertThatThrownBy(() -> invokeRequireNoGaps(chunks, 500L, 0, 1, 5))
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("빈틈")
                .hasMessageContaining("meetingId=500")
                .hasMessageContaining("기대 5개, 실제 4개");
    }

    /* 시작 seq가 요청한 firstSeq보다 뒤에서 시작하면(첫 청크 유실) 막는지 검증한다. */
    @Test
    @DisplayName("첫 청크가 유실되면 IllegalStateException을 던진다")
    void throwsWhenFirstChunkMissing() {
        List<RecordingPart> chunks = chunksOf(2, 3, 4, 5); // 1번 없음, 개수는 4로 기대치(5)와 달라 잡힘

        assertThatThrownBy(() -> invokeRequireNoGaps(chunks, 500L, 0, 1, 5))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /* 끝 seq가 요청한 lastSeq에 못 미치면(마지막 청크 유실, 아직 안 올라옴) 막는지 검증한다. */
    @Test
    @DisplayName("마지막 청크가 유실되면 IllegalStateException을 던진다")
    void throwsWhenLastChunkMissing() {
        List<RecordingPart> chunks = chunksOf(1, 2, 3, 4); // 5번 없음

        assertThatThrownBy(() -> invokeRequireNoGaps(chunks, 500L, 0, 1, 5))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /*
     * 개수는 기대치와 같은데 seq 자체가 밀려있는 경우(예: 2~6이 왔는데 1~5를 기대)도 잡는지
     * 검증한다 — 개수만 보면 통과해버릴 수 있어 처음·끝 seq를 따로 확인하는 이유다.
     */
    @Test
    @DisplayName("개수는 맞아도 seq 범위 자체가 밀려있으면 IllegalStateException을 던진다")
    void throwsWhenCountMatchesButRangeShifted() {
        List<RecordingPart> chunks = chunksOf(2, 3, 4, 5, 6); // 5개(기대치와 동일)지만 1~5가 아니다

        assertThatThrownBy(() -> invokeRequireNoGaps(chunks, 500L, 0, 1, 5))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /* 단일 청크(첫 seq == 끝 seq)만 요청한 경계 케이스도 통과하는지 검증한다. */
    @Test
    @DisplayName("단일 청크 구간도 통과한다")
    void passesForSingleChunkRange() {
        List<RecordingPart> chunks = chunksOf(3);

        assertThatCode(() -> invokeRequireNoGaps(chunks, 500L, 0, 3, 3)).doesNotThrowAnyException();
    }

    private List<RecordingPart> chunksOf(int... seqs) {
        List<RecordingPart> chunks = new ArrayList<>();
        for (int seq : seqs) {
            chunks.add(RecordingPart.create(500L, 0, seq,
                    "stt-temp/org-1/meeting-500/segments/0/parts/%04d.webm".formatted(seq),
                    "audio/webm", 1_000L, 7L));
        }
        assertThat(chunks).hasSize(seqs.length);
        return chunks;
    }

    // 리플렉션 호출은 실제 예외를 InvocationTargetException으로 감싸서 던진다 — 테스트는
    // .hasCauseInstanceOf(IllegalStateException.class)로 그 원인을 검증한다.
    private void invokeRequireNoGaps(List<RecordingPart> chunks, Long meetingId, int segmentSeq, int firstSeq,
                                     int lastSeq) throws Exception {
        Method method = SttBlockAudioAssemblyS3FfmpegAdapter.class.getDeclaredMethod(
                "requireNoGaps", List.class, Long.class, int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(adapter, chunks, meetingId, segmentSeq, firstSeq, lastSeq);
    }
}
