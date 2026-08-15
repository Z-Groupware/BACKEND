package com.module06.backend.cap.infrastructure.audio;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.module06.backend.cap.application.port.out.SttBlockAudioAssemblyPort;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.infrastructure.storage.CapS3Properties;

/*
 * SttBlockAudioAssemblyPort의 실제 구현 — 청크(opus/aac, webm·mp4 혼재 가능)를 내려받아 ffmpeg로
 * 정규화·이어붙이기·트림한 뒤 S3에 다시 올린다. SttBlockAudioAssemblyStubAdapter를 prod에서 대체한다
 * (CapS3ObjectStorageAdapter와 동일한 stub→실 어댑터 교체 패턴).
 *
 * <h2>"바이트가 서버를 안 거친다"는 CapObjectStoragePort의 원칙과 다르다</h2>
 * 여기는 처음으로 서버가 오디오 바이트·디스크·CPU를 직접 다룬다 — presign 방식이 아니라 ffmpeg가
 * 로컬 파일을 처리해야 하기 때문이다. 운영 디스크·CPU 사용량에 실제 영향을 준다.
 *
 * <h2>왜 세그먼트 단위 바이너리 concat 후 2차 concat인가(방식 D)</h2>
 * WebM 청크를 각각 ffmpeg에 넣으면 청크 경계의 컨테이너 메타데이터 때문에 앞부분이 유실될 수 있다. 그래서
 * 1) 같은 세그먼트의 청크를 seq 순서대로 바이너리 그대로 이어붙인 뒤 한 번만 16kHz/mono/16bit wav로 디코드
 * 2) 이제 세그먼트별 wav가 같은 포맷이 됐으니 2차 ffmpeg 호출로 concat + 정밀 트림(-ss/-t, 검증된 ffmpeg 옵션에
 *    맡기고 자바가 오디오 바이트 산술을 직접 하지 않는다)
 *
 * <h2>보안·안정성</h2>
 * - ProcessBuilder에 인자를 항상 배열(List)로 넘긴다 — 셸을 거치지 않아 커맨드 인젝션 경로가 없다.
 * - 임시 파일은 회의·세그먼트·UUID로 완전히 격리한다 — 스레드 풀이 동시에 여러 회사 회의를 처리할
 *   수 있어(SttBlockCutAsyncConfig), 경로가 고정이면 서로 다른 회사 오디오가 섞일 위험이 있다.
 * - ffmpeg 호출마다 타임아웃을 걸고 초과 시 강제 종료한다 — 스레드 풀이 2개뿐이라 하나만 멈춰도
 *   전체가 마비될 수 있다.
 * - ffmpeg stdout/stderr를 파이프로 안 받고 로그 파일로 리다이렉트한다 — 파이프 버퓌 가득 참으로
 *   waitFor()가 영원히 안 끝나는 고전적인 ProcessBuilder 함정을 피한다.
 */
@Component
@Profile("prod")
public class SttBlockAudioAssemblyS3FfmpegAdapter implements SttBlockAudioAssemblyPort {

    private static final Logger log = LoggerFactory.getLogger(SttBlockAudioAssemblyS3FfmpegAdapter.class);

    // 청크 하나 = 15초(문서 값) — 시간 구간을 seq 범위로 바꾸는 유일한 근거(개별 타임스탬프가 없다).
    private static final long CHUNK_DURATION_MS = 15_000L;
    private static final long CUT_WINDOW_MARGIN_MS = 20_000L;
    private static final int SAMPLE_RATE = 16_000;
    private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(30);

    private final S3Client s3Client;
    private final RecordingPartRepository recordingPartRepository;
    private final String bucket;

    public SttBlockAudioAssemblyS3FfmpegAdapter(S3Client s3Client, RecordingPartRepository recordingPartRepository,
                                                CapS3Properties properties) {
        this.s3Client = s3Client;
        this.recordingPartRepository = recordingPartRepository;
        this.bucket = properties.bucket();
    }

    @Override
    public ExtractedWindow extractCutWindow(Long companyId, Long meetingId, int segmentSeq, long targetOffsetMs,
                                            long availableUpToMs) {
        long windowStartMs = Math.max(0L, targetOffsetMs - CUT_WINDOW_MARGIN_MS);
        // 트리거는 target 지점에 도달한 그 순간 곧바로 돈다(명세 "40개 누적 시") — 뒤쪽 20초
        // 여유분은 아직 안 올라와 있을 수 있으므로, 실제 업로드된 데이터를 넘어서는 요청을
        // 하지 않는다(CodeRabbit 지적). 여유분이 없으면 그만큼 좁은 창으로 대체될 뿐, 실패하지
        // 않는다.
        long windowEndMs = Math.min(targetOffsetMs + CUT_WINDOW_MARGIN_MS, availableUpToMs);

        Path workDir = createWorkDir(meetingId, segmentSeq);
        try {
            Path wav = buildWavForRange(workDir, meetingId, segmentSeq, windowStartMs, windowEndMs);
            String s3Key = "stt-temp/org-%d/meeting-%d/segments/%d/cut-window-%d.wav"
                    .formatted(companyId, meetingId, segmentSeq, targetOffsetMs);
            uploadToS3(wav, s3Key);
            // 트림이 이미 windowStartMs 지점에서 정확히 시작하도록 잘랐으므로, 결과 wav의 첫
            // 샘플은 바로 windowStartMs다(청크 경계가 아니라).
            return new ExtractedWindow(s3Key, windowStartMs);
        } finally {
            deleteRecursively(workDir);
        }
    }

    @Override
    public String assembleBlockAudio(Long companyId, Long meetingId, int segmentSeq, int blockSeq,
                                     long startOffsetMs, long endOffsetMs) {
        Path workDir = createWorkDir(meetingId, segmentSeq);
        try {
            Path wav = buildWavForRange(workDir, meetingId, segmentSeq, startOffsetMs, endOffsetMs);
            // 명세 경로 그대로: stt-temp/org-{orgId}/meeting-{meetingId}/blocks/{blockSeq}.wav
            String s3Key = "stt-temp/org-%d/meeting-%d/blocks/%d.wav".formatted(companyId, meetingId, blockSeq);
            uploadToS3(wav, s3Key);
            return s3Key;
        } finally {
            deleteRecursively(workDir);
        }
    }

    // ── 파이프라인 ──────────────────────────────────────────────────────────

    private Path buildWavForRange(Path workDir, Long meetingId, int segmentSeq, long rangeStartMs, long rangeEndMs) {
        int firstSeq = firstChunkSeq(rangeStartMs);
        int lastSeq = lastChunkSeq(rangeEndMs);
        List<RecordingPart> chunks =
                recordingPartRepository.findInSegmentBetweenSeqs(meetingId, segmentSeq, firstSeq, lastSeq);
        requireNoGaps(chunks, meetingId, segmentSeq, firstSeq, lastSeq);

        // 1) 같은 세그먼트의 WebM 청크를 seq 순서대로 바이너리 concat한 뒤 한 번만 wav로 디코드한다.
        List<RecordingPart> orderedChunks = chunks.stream()
                .sorted(Comparator.comparingInt(RecordingPart::getSeq))
                .toList();
        Path segmentWebm = concatenateChunks(workDir, orderedChunks, "segment-%d.webm".formatted(segmentSeq));
        Path normalized = workDir.resolve("norm-segment-" + segmentSeq + ".wav");
        runFfmpeg(workDir, List.of("ffmpeg", "-y", "-i", segmentWebm.toString(),
                "-ar", String.valueOf(SAMPLE_RATE), "-ac", "1", "-c:a", "pcm_s16le", normalized.toString()));
        List<Path> normalizedWavs = List.of(normalized);

        // 2) 이제 전부 같은 포맷이니 concat demuxer로 안전하게 이어붙이고, 정확한 ms 경계로 트림한다.
        //    (자바는 seq 목록의 시작점을 기준으로 트림 시작 오프셋만 계산한다 — 오디오 바이트 자체는
        //    안 만진다.)
        long chunkListStartMs = (long) (firstSeq - 1) * CHUNK_DURATION_MS;
        long trimStartMs = rangeStartMs - chunkListStartMs;
        long trimDurationMs = rangeEndMs - rangeStartMs;

        Path fileList = workDir.resolve("concat.txt");
        writeConcatFileList(fileList, normalizedWavs);

        Path output = workDir.resolve("output.wav");
        runFfmpeg(workDir, List.of("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", fileList.toString(),
                "-ss", toSecondsArg(trimStartMs), "-t", toSecondsArg(trimDurationMs),
                "-ar", String.valueOf(SAMPLE_RATE), "-ac", "1", "-c:a", "pcm_s16le", output.toString()));

        return output;
    }

    // 요청한 [firstSeq, lastSeq] 구간에 실제로 빈틈이 없는지 확인한다(CodeRabbit 지적) — 없으면
    // ffmpeg는 있는 것만 조용히 이어붙이고, 그 결과를 "요청한 시간 구간 전체"로 착각해 트림해서
    // 시간축이 밀린 오디오를 만든다. findInSegmentBetweenSeqs가 seq 오름차순으로 준다는 전제로,
    // 개수와 처음·끝 seq만 확인하면 중간 구멍까지 전부 잡힌다.
    private void requireNoGaps(List<RecordingPart> chunks, Long meetingId, int segmentSeq, int firstSeq,
                               int lastSeq) {
        int expectedCount = lastSeq - firstSeq + 1;
        boolean gapExists = chunks.size() != expectedCount
                || chunks.get(0).getSeq() != firstSeq
                || chunks.get(chunks.size() - 1).getSeq() != lastSeq;
        if (gapExists) {
            throw new IllegalStateException(
                    "조립할 청크에 빈틈이 있습니다 — meetingId=%d segmentSeq=%d seq %d~%d 기대 %d개, 실제 %d개"
                            .formatted(meetingId, segmentSeq, firstSeq, lastSeq, expectedCount, chunks.size()));
        }
    }

    private int firstChunkSeq(long rangeStartMs) {
        return (int) (rangeStartMs / CHUNK_DURATION_MS) + 1;
    }

    private int lastChunkSeq(long rangeEndMs) {
        return (int) Math.ceil((double) rangeEndMs / CHUNK_DURATION_MS);
    }

    private String toSecondsArg(long ms) {
        return String.format(Locale.ROOT, "%.3f", ms / 1000.0);
    }

    // 회의·세그먼트·UUID로 격리한 전용 임시 디렉터리 — 동시에 도는 다른 회사 회의 처리와 절대
    // 경로가 겹치지 않게 한다(SttBlockCutAsyncConfig가 동시에 2개까지 돌린다).
    private Path createWorkDir(Long meetingId, int segmentSeq) {
        try {
            return Files.createTempDirectory("stt-block-%d-%d-%s-".formatted(meetingId, segmentSeq, UUID.randomUUID()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path concatenateChunks(Path workDir, List<RecordingPart> chunks, String fileName) {
        Path target = workDir.resolve(fileName);
        try (OutputStream outputStream = Files.newOutputStream(target)) {
            for (RecordingPart chunk : chunks) {
                try (var responseStream = s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucket).key(chunk.getS3Key()).build())) {
                    responseStream.transferTo(outputStream);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    private void writeConcatFileList(Path fileList, List<Path> wavs) {
        try {
            // concat demuxer 문법: file '경로'. 절대경로로 써서 작업 디렉터리 가정에 안 기댄다.
            List<String> lines = wavs.stream()
                    .map(p -> "file '" + p.toAbsolutePath() + "'")
                    .toList();
            Files.write(fileList, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void uploadToS3(Path file, String s3Key) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(s3Key).contentType("audio/wav").build(),
                RequestBody.fromFile(file));
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("임시 파일 삭제 실패 — {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("임시 디렉터리 정리 실패 — {}", dir, e);
        }
    }

    // ProcessBuilder에 인자를 배열로 넘긴다 — 셸을 거치지 않으므로 커맨드 인젝션 경로가 없다.
    // stdout/stderr는 파이프가 아니라 로그 파일로 리다이렉트한다 — 파이프 버퍼가 가득 차 waitFor()가
    // 멈추는 고전적인 함정을 피한다. 타임아웃 초과 시 강제 종료한다.
    private void runFfmpeg(Path workDir, List<String> command) {
        Path logFile = workDir.resolve("ffmpeg-" + UUID.randomUUID() + ".log");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            boolean finished = process.waitFor(FFMPEG_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg 타임아웃(%ds 초과) — %s"
                        .formatted(FFMPEG_TIMEOUT.toSeconds(), command));
            }
            if (process.exitValue() != 0) {
                String output = readLogQuietly(logFile);
                throw new IllegalStateException("ffmpeg 실패(exit=%d) — %s%n%s"
                        .formatted(process.exitValue(), command, output));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ffmpeg 실행이 인터럽트됐습니다 — " + command, e);
        }
    }

    private String readLogQuietly(Path logFile) {
        try {
            return Files.readString(logFile);
        } catch (IOException e) {
            return "(ffmpeg 로그를 읽을 수 없음: " + e.getMessage() + ")";
        }
    }
}
