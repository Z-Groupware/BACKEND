package com.module06.backend.cap.infrastructure.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

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

    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    private final RecordingAssemblyS3FfmpegAdapter adapter =
            new RecordingAssemblyS3FfmpegAdapter(null, null, null, null, null, null, null, null,
                    new CapS3Properties("test-bucket"));

    @TempDir
    Path tempDir;

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

    @Test
    @DisplayName("같은 segmentSeq 안에서도 EBML 헤더를 만나면 새 WebM 스트림으로 분리한다")
    void splitsSegmentByEbmlHeader() throws Exception {
        S3Client s3Client = s3ClientWithObjects(Map.of(
                "chunk-1.webm", bytes(EBML, "a"),
                "chunk-2.webm", bytes("bb"),
                "chunk-3.webm", bytes(EBML, "c"),
                "chunk-4.webm", bytes("dd")
        ));
        RecordingAssemblyS3FfmpegAdapter adapter = adapterWith(s3Client);
        List<RecordingPart> parts = List.of(
                part(2, "chunk-2.webm"),
                part(1, "chunk-1.webm"),
                part(4, "chunk-4.webm"),
                part(3, "chunk-3.webm")
        );

        List<Path> streams = invokeDownloadAndGroupByEbmlHeader(adapter, tempDir, parts, 0);

        assertThat(streams).hasSize(2);
        assertThat(Files.readAllBytes(streams.get(0))).isEqualTo(bytes(EBML, "abb"));
        assertThat(Files.readAllBytes(streams.get(1))).isEqualTo(bytes(EBML, "cdd"));
    }

    @Test
    @DisplayName("buildOgg는 EBML로 분리된 각 WebM 스트림을 각각 WAV로 정규화한 뒤 최종 concat한다")
    void buildOggDecodesEveryEbmlSubGroupAsSeparateWav() throws Exception {
        S3Client s3Client = s3ClientWithObjects(Map.of(
                "chunk-1.webm", bytes(EBML, "a"),
                "chunk-2.webm", bytes("bb"),
                "chunk-3.webm", bytes(EBML, "c"),
                "chunk-4.webm", bytes("dd")
        ));
        RecordingAssemblyS3FfmpegAdapterForTest adapter = new RecordingAssemblyS3FfmpegAdapterForTest(s3Client);
        List<RecordingPart> parts = List.of(
                part(1, "chunk-1.webm"),
                part(2, "chunk-2.webm"),
                part(3, "chunk-3.webm"),
                part(4, "chunk-4.webm")
        );

        Path output = invokeBuildOgg(adapter, tempDir, parts);

        assertThat(output).hasFileName("output.ogg");
        assertThat(Files.readString(output)).isEqualTo("ogg");
        assertThat(adapter.commands).hasSize(3);
        assertThat(Path.of(adapter.commands.get(0).get(adapter.commands.get(0).size() - 1)))
                .hasFileName("norm-segment-0-stream-0.wav");
        assertThat(Path.of(adapter.commands.get(1).get(adapter.commands.get(1).size() - 1)))
                .hasFileName("norm-segment-0-stream-1.wav");
        assertThat(adapter.commands.get(2)).contains("-f", "concat", "-c:a", "libvorbis");
        assertThat(Files.readString(tempDir.resolve("concat.txt")))
                .contains("norm-segment-0-stream-0.wav")
                .contains("norm-segment-0-stream-1.wav");
    }

    private RecordingPart part(long sizeBytes) {
        return RecordingPart.create(500L, 0, 1, "stt-temp/org-1/meeting-500/segments/0/parts/0001.webm",
                "audio/webm", sizeBytes, 7L);
    }

    private RecordingPart part(int seq, String s3Key) {
        return RecordingPart.create(500L, 0, seq, s3Key, "audio/webm", 1_000L, 7L);
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

    @SuppressWarnings("unchecked")
    private List<Path> invokeDownloadAndGroupByEbmlHeader(RecordingAssemblyS3FfmpegAdapter adapter, Path workDir,
                                                          List<RecordingPart> parts, int segmentSeq) {
        try {
            Method method = RecordingAssemblyS3FfmpegAdapter.class.getDeclaredMethod(
                    "downloadAndGroupByEbmlHeader", Path.class, List.class, int.class);
            method.setAccessible(true);
            return (List<Path>) method.invoke(adapter, workDir, parts, segmentSeq);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Path invokeBuildOgg(RecordingAssemblyS3FfmpegAdapter adapter, Path workDir, List<RecordingPart> parts) {
        try {
            Method method = RecordingAssemblyS3FfmpegAdapter.class.getDeclaredMethod("buildOgg", Path.class, List.class);
            method.setAccessible(true);
            return (Path) method.invoke(adapter, workDir, parts);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private RecordingAssemblyS3FfmpegAdapter adapterWith(S3Client s3Client) {
        return new RecordingAssemblyS3FfmpegAdapter(s3Client, null, null, null, null, null, null, null,
                new CapS3Properties("test-bucket"));
    }

    private S3Client s3ClientWithObjects(Map<String, byte[]> objects) {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0, GetObjectRequest.class);
            byte[] bytes = objects.get(request.key());
            return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                    AbortableInputStream.create(new ByteArrayInputStream(bytes)));
        });
        return s3Client;
    }

    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] bytes(byte[] prefix, String suffix) {
        byte[] suffixBytes = bytes(suffix);
        byte[] combined = Arrays.copyOf(prefix, prefix.length + suffixBytes.length);
        System.arraycopy(suffixBytes, 0, combined, prefix.length, suffixBytes.length);
        return combined;
    }

    private static class RecordingAssemblyS3FfmpegAdapterForTest extends RecordingAssemblyS3FfmpegAdapter {

        private final List<List<String>> commands = new ArrayList<>();

        private RecordingAssemblyS3FfmpegAdapterForTest(S3Client s3Client) {
            super(s3Client, null, null, null, null, null, null, null, new CapS3Properties("test-bucket"));
        }

        @Override
        protected void runFfmpeg(Path workDir, List<String> command) {
            commands.add(command);
            Path output = Path.of(command.get(command.size() - 1));
            try {
                Files.writeString(output, command.contains("libvorbis") ? "ogg" : "wav");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
