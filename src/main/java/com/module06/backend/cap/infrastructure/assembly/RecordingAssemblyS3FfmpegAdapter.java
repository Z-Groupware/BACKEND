package com.module06.backend.cap.infrastructure.assembly;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import com.module06.backend.cap.application.service.PendingAssemblyRetryRegistry;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.cap.infrastructure.storage.CapS3Properties;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;

/*
 * RecordingAssemblyPort의 실제 구현 — 회의 전체의 청크(opus/aac, webm·mp4 혼재 가능)를 내려받아
 * ffmpeg로 정규화·이어붙이기 한 뒤 재생용 recording.ogg로 인코딩해 S3에 올리고, recording 메타를
 * 등록한 뒤 원본 청크(parts)를 지운다. RecordingAssemblyStubAdapter를 prod에서 대체한다
 * (SttBlockAudioAssemblyS3FfmpegAdapter와 동일한 stub→실 어댑터 교체 패턴).
 *
 * <h2>비동기 경계는 여기 없다</h2>
 * RecordingAssemblyDispatcher(application 계층)가 @Async를 담당한다. 이 어댑터는 항상 동기
 * 구현으로 남는다 — 이 저장소의 관례(SttBlockAudioAssemblyS3FfmpegAdapter 등 모든 어댑터가
 * 동기)를 그대로 따른다.
 *
 * <h2>STT 블록 조립과 다른 점 — 트림이 없고, 품질을 낮추지 않는다</h2>
 * STT 블록(SttBlockAudioAssemblyS3FfmpegAdapter)은 10분 창을 -ss/-t로 정밀하게 잘라내고,
 * Whisper/Transcribe가 원하는 16kHz/mono로 다운샘플한다. 여기는 요청받은 seq 범위 전체를 그대로
 * 쓰므로 트림이 필요 없고, 최종 산출물이 사람이 재생·탐색하는 파일이라 STT용보다 높은 48kHz를
 * 유지한다.
 *
 * <h2>whole-file STT를 트리거하지 않는다</h2>
 * ManualRecordingService(CAP-10)와 달리 sttTriggered=false로 등록한다 — 이 경로로 조립되는
 * 녹음은 회의 진행 중 SttBlockCutTrigger가 이미 10분 블록 단위로 실시간 STT를 걸어놨다.
 *
 * <h2>실패하면 parts를 남긴다</h2>
 * recording 등록과 parts 삭제 사이에서 실패하면(드물지만) 원본 청크가 그대로 남아 있어야 사람이
 * CAP-05로 재시도할 수 있다 — 그래서 parts 삭제는 recording 등록이 성공한 뒤 마지막에 한다.
 *
 * <h2>보안·안정성은 STT 블록 어댑터와 동일한 원칙</h2>
 * ProcessBuilder 인자는 항상 배열, 임시 디렉터리는 회의·UUID로 격리, ffmpeg stdout/stderr는
 * 로그 파일로 리다이렉트, 타임아웃 초과 시 강제 종료.
 */
@Component
@Profile("prod")
public class RecordingAssemblyS3FfmpegAdapter implements RecordingAssemblyPort {

    private static final Logger log = LoggerFactory.getLogger(RecordingAssemblyS3FfmpegAdapter.class);

    // 재생용 — STT 블록(16kHz)과 달리 사람이 듣는 파일이라 원본(대부분 브라우저 MediaRecorder가
    // 48kHz opus로 녹음)에 가까운 품질을 유지한다.
    private static final int SAMPLE_RATE = 48_000;
    // 전체 회의(몇 시간 분량)를 한 번에 처리하므로 STT 블록(30초)보다 훨씬 길게 잡는다.
    // TODO: 10분도 실측 없이 정한 값이다. 청크마다 ffmpeg를 순차 실행하므로(정규화 1회 + 최종 인코딩
    // 1회) 회의 길이·청크 수에 비례해 늘어난다 — 회의 길이별 실제 처리 시간을 재보고 이 값과
    // RecordingAssemblyAsyncConfig의 풀 크기를 같이 다시 정해야 한다.
    private static final Duration FFMPEG_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration FFPROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final String FILE_NAME = "recording.ogg";
    // metering report의 revision(생성=1) — ManualRecordingService.CREATE_REVISION과 동일한
    // 상수 규약. recording.meeting_id가 UNIQUE(V4.2.1)라 이 회의의 recording 생성은 조립·수동등록
    // 중 하나로 평생 단 한 번만 일어난다 — 벽시계(clock.millis()) 대신 이 상수를 쓰면 같은 밀리초
    // 충돌·시계 역행으로 최신 report가 오판되어 무시되는 문제가 원천적으로 없다(CodeRabbit 지적).
    private static final long CREATE_REVISION = 1L;

    // 디스크 용량 가드(TODO: 이것도 실측 없이 정한 값 — FFMPEG_TIMEOUT과 같은 처지다). buildOgg가
    // 청크마다 (1) 원본 다운로드 (2) 48kHz/mono/pcm_s16le WAV 정규화본을 만들어 원본·정규화본이
    // 동시에 디스크에 쌓인다 — 원본 총합의 3배(원본 1 + WAV 1 + 최종 ogg·여유분 1)를 대략의 상한으로
    // 잡는다. 여기에 고정 여유분을 더해 최소 실행분(짧은 회의)에서도 다른 프로세스의 임시파일과
    // 부딪히지 않게 한다. RecordingAssemblyAsyncConfig가 최대 2개까지 동시 실행하므로, 실제로는
    // 이 추정치의 최대 2배가 동시에 점유될 수 있다는 점도 운영 디스크 사이징 시 감안해야 한다.
    private static final double DISK_ESTIMATE_MULTIPLIER = 3.0;
    private static final long DISK_SAFETY_MARGIN_BYTES = 500L * 1024 * 1024;

    private final S3Client s3Client;
    private final RecordingPartRepository recordingPartRepository;
    private final RecordingRepository recordingRepository;
    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapObjectStoragePort capObjectStoragePort;
    private final ReportMeetingStorageUsagePort reportMeetingStorageUsagePort;
    // 조립 완료 후 이 회의의 캡처 상태 행을 지운다(CodeRabbit 지적) — 안 지우면 같은 meetingId로
    // presign이 다시 호출될 때(재조립·비정상 재시도 등) findByMeetingId가 여전히 값을 돌려줘
    // "새 회의 시작" 판정(CaptureUploadService의 저장 용량 한도 확인)을 건너뛴다.
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final PendingAssemblyRetryRegistry pendingAssemblyRetryRegistry;
    private final String bucket;

    public RecordingAssemblyS3FfmpegAdapter(S3Client s3Client, RecordingPartRepository recordingPartRepository,
                                            RecordingRepository recordingRepository,
                                            MeetingReferenceRepository meetingReferenceRepository,
                                            CapObjectStoragePort capObjectStoragePort,
                                            ReportMeetingStorageUsagePort reportMeetingStorageUsagePort,
                                            CaptureUploadStateRepository captureUploadStateRepository,
                                            PendingAssemblyRetryRegistry pendingAssemblyRetryRegistry,
                                            CapS3Properties properties) {
        this.s3Client = s3Client;
        this.recordingPartRepository = recordingPartRepository;
        this.recordingRepository = recordingRepository;
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.capObjectStoragePort = capObjectStoragePort;
        this.reportMeetingStorageUsagePort = reportMeetingStorageUsagePort;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.pendingAssemblyRetryRegistry = pendingAssemblyRetryRegistry;
        this.bucket = properties.bucket();
    }

    @Override
    public void startAssembly(Long meetingId, int lastSegmentSeq, int lastSeq) {
        try {
            Long companyId = meetingReferenceRepository.findCompanyId(meetingId)
                    .orElseThrow(() -> new IllegalStateException("회의를 찾을 수 없습니다 — meetingId=" + meetingId));

            List<RecordingPart> parts = collectAllParts(meetingId, lastSegmentSeq, lastSeq);
            if (parts.isEmpty()) {
                log.info("조립할 청크가 없어 조립을 생략한다 — meetingId={}", meetingId);
                return;
            }

            requireSufficientDiskSpace(meetingId, parts);

            Path workDir = createWorkDir(meetingId);
            try {
                Path finalOgg = buildOgg(workDir, parts);
                int durationSec = probeDurationSeconds(workDir, finalOgg);
                long sizeBytes = Files.size(finalOgg);
                String s3Key = "recordings/org-%d/meeting-%d/%s".formatted(companyId, meetingId, FILE_NAME);
                uploadToS3(finalOgg, s3Key);

                recordingRepository.save(Recording.registerWithDuration(
                        meetingId, FILE_NAME, s3Key, sizeBytes, durationSec, false));

                // metering에 최종 크기로 report — 원본 청크 총합보다 보통 작아진다(재인코딩). 별도
                // try/catch로 감싼다(CodeRabbit 지적) — 바깥 try/catch에 맡기면 report 실패가 곧바로
                // 아래 parts 정리를 건너뛰게 만든다. recording은 이미 저장됐는데 원본 청크가 영영
                // 안 지워지는 쪽보다, metering 원장 하나 누락되는 쪽이 훨씬 덜 해롭다.
                reportStorageUsageBestEffort(companyId, meetingId, sizeBytes);

                // 성공 후에만 parts 삭제(되돌릴 수 없음) — 위 등록이 실패하면 여기 도달하지 않아
                // 원본 청크가 그대로 남고, 사람이 CAP-05로 재시도할 수 있다.
                // S3 객체를 먼저 지운다 — DB 행(recording_part)을 먼저 지우면 s3Key 참조가 사라져서,
                // 그 뒤 S3 삭제가 실패한 조각은 다시는 찾을 수 없는 고아 객체로 영영 남는다(CodeRabbit 지적).
                deletePartObjectsBestEffort(parts);
                recordingPartRepository.deleteByMeetingId(meetingId);
                deleteCaptureStateBestEffort(meetingId);

                // 이전에 디스크 부족으로 대기 등록된 적이 있다면 지운다 — 이번엔 성공했으니
                // 남아있는 시도 횟수가 나중에 사람이 CAP-05로 다시 부를 때 헷갈리면 안 된다.
                pendingAssemblyRetryRegistry.clear(meetingId);

                log.info("회의 녹음 조립 완료 — meetingId={} durationSec={} sizeBytes={}",
                        meetingId, durationSec, sizeBytes);
            } finally {
                deleteRecursively(workDir);
            }
        } catch (InsufficientDiskSpaceException e) {
            // 다른 실패(ffmpeg 오류 등)와 다르게 다룬다 — 대개 "지금 동시에 도는 다른 조립이
            // 디스크를 쓰고 있어서" 생기는 일시적 상황이라, RecordingAssemblyRetryScheduler가
            // 몇 분 뒤 다시 시도하도록 등록만 하고 넘어간다(TODO: 재시도 간격·횟수 상한 모두
            // FFMPEG_TIMEOUT처럼 실측 없이 정한 값 — RecordingAssemblyRetryScheduler 주석 참고).
            log.warn("디스크 용량 부족으로 조립 연기, 재시도 대기 등록 — meetingId={}", meetingId, e);
            pendingAssemblyRetryRegistry.recordFailure(meetingId, lastSegmentSeq, lastSeq);
        } catch (RuntimeException | IOException e) {
            // best-effort — 실패해도 회의 종료/CAP-05 응답을 되돌리지 않는다. 사람이 CAP-05로 재시도한다.
            log.error("회의 녹음 조립 실패 — meetingId={}", meetingId, e);
            // 디스크 부족이 아닌 이유로 실패했다(CodeRabbit 지적) — 이 시도가 재시도 스케줄러가
            // 디스패치한 것이었다면(inFlight=true) 여기서 안 풀어주면 다음 주기부터 영원히
            // "진행 중"으로 오판돼 재시도도 MAX_ATTEMPTS 소진도 못 밟는다. 애초에 대기 목록에
            // 없던 첫 시도의 실패라면 이 호출은 아무 일도 안 한다(releaseInFlight의 계약).
            pendingAssemblyRetryRegistry.releaseInFlight(meetingId);
        }
    }

    // ── 파이프라인 ──────────────────────────────────────────────────────────

    // 세그먼트 0..lastSegmentSeq 전체를 순서대로 모은다. 중간 세그먼트는 그 세그먼트의 최대 순번까지,
    // 마지막 세그먼트는 호출자가 넘긴 lastSeq까지 — RecordingGapChecker와 동일한 범위 규칙이다
    // (이 메서드가 불리기 전에 이미 그 검증을 통과했다는 전제).
    private List<RecordingPart> collectAllParts(Long meetingId, int lastSegmentSeq, int lastSeq) {
        List<RecordingPart> all = new ArrayList<>();
        for (int segment = 0; segment <= lastSegmentSeq; segment++) {
            int target = (segment == lastSegmentSeq) ? lastSeq
                    : RecordingPartRepository.maxOf(recordingPartRepository.findSeqsInSegment(meetingId, segment));
            if (target <= 0) {
                continue;
            }
            all.addAll(recordingPartRepository.findInSegmentBetweenSeqs(meetingId, segment, 1, target));
        }
        return all;
    }

    // metering에 최종 크기로 report한다 — 실패해도 조립 자체(recording 등록·parts 정리)는 계속한다.
    // projectId 조회도 이 try 안에서 한다 — 밖에서 하면 조회 실패(사실상 안 일어나지만)가 바깥
    // catch로 튀어 그 뒤의 parts 정리까지 건너뛰게 만든다(바로 위 report 자체와 같은 이유).
    private void reportStorageUsageBestEffort(Long companyId, Long meetingId, long sizeBytes) {
        try {
            Long projectId = meetingReferenceRepository.findProjectId(meetingId)
                    .orElseThrow(() -> new IllegalStateException("회의를 찾을 수 없습니다 — meetingId=" + meetingId));
            reportMeetingStorageUsagePort.report(new ReportMeetingStorageUsageCommand(
                    companyId, projectId, meetingId, sizeBytes, CREATE_REVISION));
        } catch (RuntimeException e) {
            log.warn("저장 용량 미터링 기록 실패 — 조립은 계속 진행. meetingId={}", meetingId, e);
        }
    }

    // capture_upload_state 삭제 실패를 별도 try/catch로 격리한다(CodeRabbit 지적) — 바깥 catch에
    // 맡기면 이미 끝난 recording·parts 정리가 성공했는데도 삭제 실패가 로그만 남기고 조용히 삼켜져
    // "저장 용량 한도 확인 우회" 위험이 눈에 안 띈다. ERROR로 남겨 운영에서 놓치지 않게 한다 — 이
    // 회의는 조립이 이미 끝나 재시도(CAP-05)해도 parts가 비어 있어(Line 124) 다시 여기로 오지 않으므로,
    // 실패하면 사람이 직접 capture_upload_state 행을 지워야 한다.
    private void deleteCaptureStateBestEffort(Long meetingId) {
        try {
            captureUploadStateRepository.deleteByMeetingId(meetingId);
        } catch (RuntimeException e) {
            log.error("캡처 상태 삭제 실패 — 조립은 완료됐으나 이 meetingId는 저장 용량 한도 확인을 "
                    + "계속 건너뛴다. 수동으로 capture_upload_state 행을 지워야 한다. meetingId={}", meetingId, e);
        }
    }

    // 청크 S3 객체를 하나씩 지운다 — 한둘이 실패해도 나머지는 계속 지운다(부분 실패로 전체 조립을
    // 실패 처리하면 이미 완료된 recording 등록까지 되돌릴 수 없으면서 청크만 영영 안 지워진다).
    // 실패한 키는 로그로 남긴다 — 재시도 경로는 없다(recording이 이미 등록돼 자동 재트리거가 없기
    // 때문, CodeRabbit 지적). 운영에서 반복되면 별도 정리 작업이 필요하다.
    private void deletePartObjectsBestEffort(List<RecordingPart> parts) {
        for (RecordingPart part : parts) {
            try {
                capObjectStoragePort.deleteRecording(part.getS3Key());
            } catch (RuntimeException e) {
                log.warn("청크 S3 객체 삭제 실패 — s3Key={}", part.getS3Key(), e);
            }
        }
    }

    private Path buildOgg(Path workDir, List<RecordingPart> parts) {
        // 1) 청크마다 개별적으로 정규화(코덱이 뭐든 각자 안전하게 디코드) —
        // SttBlockAudioAssemblyS3FfmpegAdapter와 동일한 이유(방식 D).
        List<Path> normalizedWavs = new ArrayList<>();
        for (RecordingPart part : parts) {
            Path source = downloadChunk(workDir, part);
            Path normalized = workDir.resolve("norm-%d-%d.wav".formatted(part.getSegmentSeq(), part.getSeq()));
            runFfmpeg(workDir, List.of("ffmpeg", "-y", "-i", source.toString(),
                    "-ar", String.valueOf(SAMPLE_RATE), "-ac", "1", "-c:a", "pcm_s16le", normalized.toString()));
            normalizedWavs.add(normalized);
        }

        // 2) 전부 같은 포맷이니 concat demuxer로 이어붙이면서 곧바로 ogg(vorbis)로 인코딩한다 —
        // 요청 범위 전체를 그대로 쓰므로 STT 블록과 달리 -ss/-t 트림이 필요 없다.
        Path fileList = workDir.resolve("concat.txt");
        writeConcatFileList(fileList, normalizedWavs);

        Path output = workDir.resolve("output.ogg");
        runFfmpeg(workDir, List.of("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", fileList.toString(),
                "-c:a", "libvorbis", "-q:a", "5", output.toString()));
        return output;
    }

    // ffprobe로 최종 산출물의 실제 길이를 읽는다 — recording.durationSec은 클라이언트가 주장한 값이
    // 아니라 여기서 실측한 값이어야 재생바 탐색이 정확하다.
    //
    // stdout·stderr를 각각 별도 로그 파일로 리다이렉트한다(CodeRabbit 지적 두 건) — (1) 합치면
    // exit=0이어도 stderr 경고가 duration 숫자와 섞여 파싱이 깨진다. (2) stdout을 waitFor() 전에
    // 파이프로 직접 읽으면, ffprobe가 멈췄을 때 그 읽기 자체가 영원히 블로킹돼 뒤의 타임아웃
    // 로직에 도달하지 못한다 — 파일로 리다이렉트하고 waitFor()가 끝난 뒤에만 읽어야 안전하다.
    private int probeDurationSeconds(Path workDir, Path file) {
        Path errorLog = workDir.resolve("ffprobe-" + UUID.randomUUID() + ".err.log");
        Path outputLog = workDir.resolve("ffprobe-" + UUID.randomUUID() + ".out.log");
        try {
            Process process = new ProcessBuilder(List.of("ffprobe", "-v", "error", "-show_entries",
                    "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", file.toString()))
                    .redirectError(errorLog.toFile())
                    .redirectOutput(outputLog.toFile())
                    .start();

            boolean finished = process.waitFor(FFPROBE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("ffprobe 타임아웃(%ds 초과) — %s"
                        .formatted(FFPROBE_TIMEOUT.toSeconds(), file));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ffprobe 실패(exit=%d) — %s"
                        .formatted(process.exitValue(), readLogQuietly(errorLog)));
            }
            return parseDurationSeconds(readLogQuietly(outputLog), file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ffprobe 실행이 인터럽트됐습니다 — " + file, e);
        }
    }

    // 컨테이너에 duration 메타데이터가 없으면 ffprobe는 exit=0인데 "N/A"를 찍는다(CodeRabbit 지적) —
    // 숫자로 파싱되지 않으면 무음 길이로 등록하지 않고 명확히 실패시킨다.
    private int parseDurationSeconds(String stdout, Path file) {
        String lastLine = null;
        for (String line : stdout.split("\\R")) {
            if (!line.isBlank()) {
                lastLine = line.trim();
            }
        }
        if (lastLine == null) {
            throw new IllegalStateException("ffprobe가 duration을 출력하지 않았습니다 — " + file);
        }
        try {
            return (int) Math.round(Double.parseDouble(lastLine));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ffprobe duration 파싱 실패(값=" + lastLine + ") — " + file, e);
        }
    }

    // 임시 디렉터리를 실제로 만들기 전에, 원본 다운로드+정규화 WAV를 담을 여유가 있는지 먼저
    // 확인한다 — 부족한 채로 buildOgg를 시작하면 회의 몇 시간 분량을 다 받은 뒤에야 ENOSPC로
    // 실패하고(디스크 I/O·네트워크만 낭비), 그마저도 이 임시 디렉터리만의 문제가 아니라 같은
    // 파일시스템을 쓰는 다른 프로세스까지 같이 영향받을 수 있다.
    private void requireSufficientDiskSpace(Long meetingId, List<RecordingPart> parts) throws IOException {
        long estimatedRequiredBytes = estimateRequiredBytes(parts);
        long usableBytes = Files.getFileStore(Path.of(System.getProperty("java.io.tmpdir"))).getUsableSpace();
        if (usableBytes < estimatedRequiredBytes) {
            throw new InsufficientDiskSpaceException(
                    "임시 디렉터리 여유 공간 부족 — meetingId=%d 필요(추정)=%d바이트 가용=%d바이트"
                            .formatted(meetingId, estimatedRequiredBytes, usableBytes));
        }
    }

    // 순수 계산만 뽑아둔다 — 실제 파일시스템 호출(Files.getFileStore) 없이 이 추정 공식만
    // 단위 테스트하기 위해서다(RecordingPart가 개별 청크를 2MB로 제한해 "디스크보다 큰 값"을
    // 실제 파트로 만들 수 없으므로, 그 경계는 이 메서드 하나로 검증한다).
    private long estimateRequiredBytes(List<RecordingPart> parts) {
        long totalSourceBytes = parts.stream().mapToLong(RecordingPart::getSizeBytes).sum();
        return (long) (totalSourceBytes * DISK_ESTIMATE_MULTIPLIER) + DISK_SAFETY_MARGIN_BYTES;
    }

    // 회의·UUID로 격리한 전용 임시 디렉터리 — 동시에 여러 회의가 조립될 수 있어 경로가 겹치면
    // 안 된다(RecordingAssemblyAsyncConfig가 동시에 2개까지 돌린다).
    private Path createWorkDir(Long meetingId) {
        try {
            return Files.createTempDirectory("recording-assembly-%d-%s-".formatted(meetingId, UUID.randomUUID()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path downloadChunk(Path workDir, RecordingPart part) {
        Path target = workDir.resolve("chunk-%d-%d%s".formatted(part.getSegmentSeq(), part.getSeq(),
                extensionOf(part.getS3Key())));
        try (var responseStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(part.getS3Key()).build())) {
            Files.copy(responseStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    // 파일명 부분(마지막 '/' 뒤)에서만 확장자를 찾는다(CodeRabbit 지적) — 키 전체에서 찾으면 상위
    // 경로 세그먼트에 점이 있을 때(예: v1.0/) 그 점을 확장자로 오인해 존재하지 않는 하위 디렉터리
    // 경로가 만들어지고 다운로드가 실패한다.
    private String extensionOf(String s3Key) {
        int lastSlash = s3Key.lastIndexOf('/');
        int dot = s3Key.lastIndexOf('.');
        return dot > lastSlash ? s3Key.substring(dot) : "";
    }

    private void writeConcatFileList(Path fileList, List<Path> wavs) {
        try {
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
                PutObjectRequest.builder().bucket(bucket).key(s3Key).contentType("audio/ogg").build(),
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
                throw new IllegalStateException("ffmpeg 실패(exit=%d) — %s%n%s"
                        .formatted(process.exitValue(), command, readLogQuietly(logFile)));
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
            return "(로그를 읽을 수 없음: " + e.getMessage() + ")";
        }
    }
}
