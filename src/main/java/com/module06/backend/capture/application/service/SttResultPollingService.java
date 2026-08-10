package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository.PendingBlock;
import com.module06.backend.capture.application.port.out.SttJobResultPort;
import com.module06.backend.capture.application.port.out.SttJobResultPort.SttJobOutcome;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.TranscriptRepository.NewUtterance;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.TranscriptSegmenter;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Segment;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;

/*
 * 제출해 둔 STT 잡의 결과를 가져와 블록 상태를 옮기고 정본을 적재한다.
 *
 * <h2>적재가 DONE 보다 먼저다 — 이 순서가 이 클래스의 요점이다</h2>
 * 정본을 넣기 전에 블록을 DONE 으로 닫으면 **분석 시작 관문이 통과된다**
 * (AnalysisOrchestrator 의 미완 블록 검사). 그 순간 분석이 시작되면 앞부분만 있는 정본으로
 * 요약·배정이 만들어지고 그게 "분석 완료"로 기록된다 — 뒷부분의 할 일은 어디에도 없고
 * 아무도 그 사실을 모른다. 이 파이프라인에서 가장 위험한 실패 방향이다.
 *
 * <h2>블록 하나의 실패가 나머지를 막지 않는다</h2>
 * 한 주기에 여러 블록을 훑는다. 그중 하나에서 예외가 나면 그 블록만 로그로 남기고 다음으로
 * 간다 — 워커가 통째로 죽으면 밀린 잡 전부가 그 블록 하나 때문에 영구히 멈춘다.
 *
 * <h2>못 읽은 것과 실패한 것을 구분한다</h2>
 * 제공자를 못 읽었을 때(UNAVAILABLE) 상태를 바꾸지 않는다. 네트워크가 흔들린 것을 실패로
 * 접으면 정상적으로 돌고 있던 잡이 FAILED 로 닫히고, 사람이 재처리를 눌러 **같은 구간에
 * 요금이 두 번 나간다.**
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttResultPollingService {

    /*
     * 한 주기에 볼 블록 수. 상한이 없으면 밀린 잡이 많을 때 한 주기가 끝나지 않고, fixedDelay
     * 의 겹침 방어가 의미를 잃는다(TupleVectorSyncScheduler 주석과 같은 이유).
     */
    private static final int BATCH_LIMIT = 50;

    /* 제공자가 그 이름의 잡을 모른다. 화면이 이 코드로 문구를 고른다(제공자 메시지가 아니다). */
    private static final String ERROR_JOB_NOT_FOUND = "JOB_NOT_FOUND";

    /* 제출은 됐다는데 잡 이름이 비어 있다 — 결과를 되짚을 방법이 없는 행이다. */
    private static final String ERROR_NO_JOB_NAME = "NO_JOB_NAME";

    private final SttBlockRepository sttBlockRepository;
    private final SttJobResultPort sttJobResultPort;
    private final TranscriptRepository transcriptRepository;

    /*
     * 한 주기 돈다.
     *
     * @return 이번에 끝맺은 블록 수(DONE 또는 FAILED). 아직 도는 블록은 세지 않는다 —
     *         "몇 건이 진행 중인가"는 CAP-06 이 답하는 값이고, 워커 로그는 변화만 남긴다
     */
    public int pollOnce() {
        List<PendingBlock> blocks = sttBlockRepository.findUnfinished(BATCH_LIMIT);
        if (blocks.isEmpty()) {
            return 0;
        }

        int settled = 0;
        for (PendingBlock block : blocks) {
            try {
                if (settle(block)) {
                    settled++;
                }
            } catch (RuntimeException e) {
                // 이 블록만 남기고 넘어간다 — 워커가 죽으면 밀린 잡 전부가 함께 멈춘다.
                log.error("STT 결과 처리 실패 — meetingId={} blockSeq={} job={}",
                        block.meetingId(), block.blockSeq(), block.providerJobName(), e);
            }
        }
        return settled;
    }

    /* @return 이 블록을 끝맺었으면 true */
    private boolean settle(PendingBlock block) {
        /*
         * 아직 제출하지 않은 블록이다. 실패로 닫지 않는다 — 제출하는 쪽이 곧 QUEUED 로 옮길
         * 자리이고, 여기서 FAILED 로 만들면 사람이 재처리를 눌러야 제출이 시작된다.
         * (지금은 PENDING 을 만드는 경로가 없지만, 상태가 있으니 여기서 뜻을 정해 둔다.)
         */
        if (block.status() == SttBlockStatus.PENDING) {
            return false;
        }

        if (block.providerJobName() == null || block.providerJobName().isBlank()) {
            log.error("잡 이름이 없는 블록 — 결과를 되짚을 수 없다. meetingId={} blockSeq={}",
                    block.meetingId(), block.blockSeq());
            return sttBlockRepository.markFailed(block.id(), ERROR_NO_JOB_NAME);
        }

        SttJobOutcome outcome = sttJobResultPort.fetch(block.providerJobName());
        return switch (outcome.state()) {
            case QUEUED -> false;
            case RUNNING -> {
                // 전이 실패는 정상이다 — 그 사이 사람이 재처리를 눌러 QUEUED 로 되돌렸을 수 있다.
                sttBlockRepository.markRunning(block.id());
                yield false;
            }
            case COMPLETED -> {
                loadTranscript(block, outcome.words());
                yield sttBlockRepository.markDone(block.id());
            }
            case FAILED -> {
                log.warn("STT 실패 — meetingId={} blockSeq={} job={} 사유={}",
                        block.meetingId(), block.blockSeq(), block.providerJobName(), outcome.errorCode());
                yield sttBlockRepository.markFailed(block.id(), outcome.errorCode());
            }
            /*
             * 제공자가 그 이름을 모른다. **실패로 닫는다** — 우리만 QUEUED 로 남은 상태이고
             * (제출 응답을 못 받았거나 보관 기간이 지났다) 그대로 두면 영원히 안 움직인다.
             * FAILED 가 되어야 STT-04 의 대상이 되고, 사람이 새 이름으로 다시 제출할 수 있다.
             */
            case UNKNOWN -> {
                log.warn("제공자가 모르는 잡 — 우리 상태와 어긋났다. meetingId={} blockSeq={} job={}",
                        block.meetingId(), block.blockSeq(), block.providerJobName());
                yield sttBlockRepository.markFailed(block.id(), ERROR_JOB_NOT_FOUND);
            }
            // 네트워크·권한 문제다. 상태를 바꾸지 않고 다음 주기에 다시 본다.
            case UNAVAILABLE -> false;
        };
    }

    /*
     * 정본을 적재한다 — **DONE 으로 닫기 전에.**
     *
     * <h2>오프셋을 회의 기준으로 옮긴다</h2>
     * 제공자는 자기가 받은 오디오(=블록 하나)의 처음을 0 으로 준다. 블록 시작을 더하지 않으면
     * 두 번째 블록부터 발화가 회의 맨 앞으로 겹쳐 쌓이고, 그 뒤 전부가 무너진다 —
     * 자막 시간 매칭(L1)도 근거 발화 ID 도 이 좌표계를 쓴다.
     *
     * <h2>적재 뒤 markDone 이 false 여도 되돌리지 않는다</h2>
     * 그 사이 사람이 재처리를 눌렀다는 뜻이고, 그러면 새 잡이 완료될 때 같은 블록의 행을 다시
     * 교체한다(replaceBlockTranscript). 지금 넣은 것이 잠깐 남아 있는 것은 해롭지 않다 —
     * 오히려 지우면 새 잡이 끝날 때까지 그 구간이 비어 있게 된다.
     */
    private void loadTranscript(PendingBlock block, List<Word> blockWords) {
        List<Word> meetingWords = new ArrayList<>(blockWords.size());
        for (Word word : blockWords) {
            meetingWords.add(new Word(
                    word.startMs() + block.startOffsetMs(),
                    word.endMs() + block.startOffsetMs(),
                    word.text(), word.punctuation()));
        }

        List<Segment> segments = TranscriptSegmenter.segment(meetingWords);
        List<NewUtterance> utterances = segments.stream()
                .map(segment -> new NewUtterance(
                        segment.startOffsetMs(), segment.endOffsetMs(), segment.text()))
                .toList();

        int loaded = transcriptRepository.replaceBlockTranscript(
                block.meetingId(), block.blockSeq(), utterances);

        if (loaded == 0) {
            /*
             * 인식이 아무것도 못 건졌다. 침묵 구간이면 정상이지만 **조용히 넘기지 않는다** —
             * 오디오 조립이 빈 파일을 만든 경우도 같은 모양이고, 그건 로그가 유일한 단서다.
             */
            log.warn("STT 결과가 비어 있다 — meetingId={} blockSeq={} job={}",
                    block.meetingId(), block.blockSeq(), block.providerJobName());
            return;
        }
        log.info("정본 적재 — meetingId={} blockSeq={} 발화 {}건", block.meetingId(), block.blockSeq(), loaded);
    }
}
