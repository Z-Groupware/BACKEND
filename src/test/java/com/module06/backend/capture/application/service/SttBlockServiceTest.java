package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttJobPort;
import com.module06.backend.capture.application.usecase.RetrySttBlockUseCase.RetryAccepted;
import com.module06.backend.capture.application.usecase.RetrySttBlockUseCase.RetrySttBlockCommand;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttCutReason;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STT-03 · STT-04.
 *
 * <p>검증의 축은 <b>다시 돌리면 안 되는 것이 안 돌아가는가</b>다. 성공했거나 아직 도는 블록을
 * 다시 돌리면 같은 구간에 STT 요금이 두 번 나가고, 이미 들어온 발화 위에 같은 결과가 덮인다.
 * 화면이 버튼을 가려도 blockSeq 를 직접 넣는 경로가 남는다.
 */
class SttBlockServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;

    @Test
    @DisplayName("실패한 블록은 QUEUED 로 접수되고 시도 횟수가 오른다")
    void 실패한_블록은_재처리된다() {
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));
        RecordingJobPort jobs = new RecordingJobPort();

        RetryAccepted accepted = service(blocks, jobs)
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null));

        assertThat(accepted.status()).isEqualTo(SttBlockStatus.QUEUED);
        assertThat(accepted.retryCount()).isEqualTo(3);
        assertThat(jobs.submitted).hasSize(1);
    }

    @Test
    @DisplayName("잡 이름에 시도 횟수가 들어간다 — 계정 내 유일해야 해서 같은 이름은 제출이 거절된다")
    void 잡_이름에_시도_횟수가_들어간다() {
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));
        RecordingJobPort jobs = new RecordingJobPort();

        service(blocks, jobs).retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null));

        // 명세의 예시 그대로다 — meeting-500-block-3-r3.
        assertThat(jobs.submitted.get(0).providerJobName()).isEqualTo("meeting-500-block-3-r3");
        // 저장된 이름과 제출한 이름이 같아야 한다. 갈리면 결과 콜백을 되짚지 못한다.
        assertThat(blocks.savedJobName).isEqualTo("meeting-500-block-3-r3");
    }

    @Test
    @DisplayName("네임스페이스를 주면 잡 이름 앞에 붙는다 — 재시드한 DB가 옛 잡과 안 부딪히게")
    void 네임스페이스가_잡_이름에_붙는다() {
        // 이름은 meetingId로만 만들어지는데 AWS 쪽 잡 이름은 DB보다 오래 산다 — 재시드로
        // meetingId가 재사용되면 옛 잡과 충돌해서 제출 자체가 거절된다(2026-08-18 P1).
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));
        RecordingJobPort jobs = new RecordingJobPort();

        new SttBlockService(blocks, jobs, new MeetingAccessGuard((companyId, meetingId) -> true),
                new SttJobNameFactory("stg-seed7"))
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null));

        assertThat(jobs.submitted.get(0).providerJobName()).isEqualTo("stg-seed7-meeting-500-block-3-r3");
        // 저장된 이름도 같아야 한다 — 폴링이 되짚는 것은 제출한 이름이 아니라 저장된 이름이다.
        assertThat(blocks.savedJobName).isEqualTo("stg-seed7-meeting-500-block-3-r3");
    }

    @Test
    @DisplayName("실패하지 않은 블록은 409 — 같은 구간에 요금이 두 번 나가고 발화가 덮인다")
    void 실패하지_않은_블록은_거절한다() {
        FakeBlockRepository blocks = new FakeBlockRepository(
                block(3, SttBlockStatus.DONE, 0, "meeting-500/blocks/3.wav"));
        RecordingJobPort jobs = new RecordingJobPort();

        assertThatThrownBy(() -> service(blocks, jobs)
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null)))
                .isInstanceOf(BusinessException.class);

        // 제출도 상태 변경도 없어야 한다.
        assertThat(jobs.submitted).isEmpty();
        assertThat(blocks.savedJobName).isNull();
    }

    @Test
    @DisplayName("돌고 있는 블록도 막는다 — RUNNING 을 다시 제출하면 같은 잡이 두 벌 돈다")
    void 돌고_있는_블록도_거절한다() {
        FakeBlockRepository blocks = new FakeBlockRepository(
                block(3, SttBlockStatus.RUNNING, 1, "meeting-500/blocks/3.wav"));

        assertThatThrownBy(() -> service(blocks, new RecordingJobPort())
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("오디오가 없으면 409 — 제출해도 시도 횟수만 오르고 같은 자리로 돌아온다")
    void 오디오가_없으면_거절한다() {
        FakeBlockRepository blocks = new FakeBlockRepository(
                block(3, SttBlockStatus.FAILED, 2, null));
        RecordingJobPort jobs = new RecordingJobPort();

        assertThatThrownBy(() -> service(blocks, jobs)
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null)))
                .isInstanceOf(BusinessException.class);

        assertThat(jobs.submitted).isEmpty();
    }

    @Test
    @DisplayName("provider 를 생략하면 쓰던 제공자를 유지한다 — 조용히 바뀌면 정확도 차이의 원인을 못 찾는다")
    void 제공자를_생략하면_유지한다() {
        FakeBlockRepository blocks = new FakeBlockRepository(
                new SttBlockRepository.SttBlockView(1L, 3, 0, 600_000, SttBlockStatus.FAILED,
                        "whisper", SttCutReason.VAD_SILENCE, 1, "JOB_FAILED",
                        "meeting-500/blocks/3.wav", null, null));
        RecordingJobPort jobs = new RecordingJobPort();

        service(blocks, jobs).retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null));

        assertThat(jobs.submitted.get(0).provider()).isEqualTo("whisper");
    }

    @Test
    @DisplayName("provider 를 주면 그 제공자로 바꿔 돌린다 — 같은 제공자로 세 번 실패한 블록은 네 번째도 같다")
    void 제공자를_주면_바꿔_돌린다() {
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));
        RecordingJobPort jobs = new RecordingJobPort();

        service(blocks, jobs).retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, "whisper"));

        assertThat(jobs.submitted.get(0).provider()).isEqualTo("whisper");
    }

    @Test
    @DisplayName("동시 요청에서 진 쪽은 제출하지 않는다 — 둘 다 보내면 같은 잡 이름이 두 번 나간다")
    void 전이에_실패하면_제출하지_않는다() {
        /*
         * 조회와 갱신 사이에 다른 요청이 끼어들어 이미 전이시킨 상황. 실물에서는 저장소가
         * 쓰기 잠금을 걸고 읽은 값이 그대로인지 확인해 걸러낸다(compare-and-set).
         *
         * 여기서 제출까지 나가면 **둘이 같은 retryCount 로 만든 같은 잡 이름을 두 번 보내고**,
         * AWS 는 계정 내 중복 이름을 거절한다 — 잡 이름에 횟수를 넣어 막으려던 그 상황이다.
         */
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));
        blocks.loseRace = true;
        RecordingJobPort jobs = new RecordingJobPort();

        assertThatThrownBy(() -> service(blocks, jobs)
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null)))
                .isInstanceOf(BusinessException.class);

        assertThat(jobs.submitted).isEmpty();
    }

    @Test
    @DisplayName("전이에 읽은 시도 횟수를 함께 넘긴다 — 상태만 보면 한 바퀴 돈 뒤에도 통과한다")
    void 읽은_시도_횟수를_함께_넘긴다() {
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));

        service(blocks, new RecordingJobPort()).retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null));

        // 조회 시점의 값(2)이 그대로 넘어가야 한다. 그 사이 누가 올렸으면 전이가 거절된다.
        assertThat(blocks.expectedRetryCount).isEqualTo(2);
    }

    @Test
    @DisplayName("그 회의의 블록이 아니면 404 — 회의 관문은 blockSeq 를 보지 않는다")
    void 없는_블록은_404() {
        assertThatThrownBy(() -> service(new FakeBlockRepository(), new RecordingJobPort())
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 99, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 회사 회의는 관문에서 막는다 — 조회조차 하지 않는다")
    void 다른_회사_회의는_막는다() {
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));

        assertThatThrownBy(() -> new SttBlockService(blocks, new RecordingJobPort(),
                new MeetingAccessGuard((companyId, meetingId) -> false), new SttJobNameFactory(""))
                .retry(new RetrySttBlockCommand(COMPANY, MEETING, 3, null)))
                .isInstanceOf(BusinessException.class);

        assertThat(blocks.queried).isFalse();
    }

    @Test
    @DisplayName("조회도 관문을 먼저 지난다 — stt_block 에는 회사 컬럼이 없다")
    void 조회도_관문을_지난다() {
        FakeBlockRepository blocks = new FakeBlockRepository(failed(3, 2));

        assertThatThrownBy(() -> new SttBlockService(blocks, new RecordingJobPort(),
                new MeetingAccessGuard((companyId, meetingId) -> false), new SttJobNameFactory(""))
                .getSttBlocks(COMPANY, MEETING))
                .isInstanceOf(BusinessException.class);

        assertThat(blocks.queried).isFalse();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private SttBlockService service(FakeBlockRepository blocks, RecordingJobPort jobs) {
        return new SttBlockService(blocks, jobs, new MeetingAccessGuard((companyId, meetingId) -> true),
                new SttJobNameFactory(""));
    }

    private static SttBlockRepository.SttBlockView failed(int blockSeq, int retryCount) {
        return block(blockSeq, SttBlockStatus.FAILED, retryCount, "meeting-500/blocks/3.wav");
    }

    private static SttBlockRepository.SttBlockView block(int blockSeq, SttBlockStatus status,
                                                         int retryCount, String audioS3Key) {
        return new SttBlockRepository.SttBlockView(
                1L, blockSeq, 0, 600_000, status, "aws-transcribe",
                SttCutReason.VAD_SILENCE, retryCount,
                status == SttBlockStatus.FAILED ? "JOB_FAILED" : null,
                audioS3Key, null, null);
    }

    private static final class FakeBlockRepository implements SttBlockRepository {

        private final List<SttBlockView> blocks;
        private boolean queried;
        private String savedJobName;
        /* 다른 요청이 먼저 가져간 상황. 실물에서는 잠금 뒤 재확인이 이 판정을 한다. */
        private boolean loseRace;
        private int expectedRetryCount = -1;

        private FakeBlockRepository(SttBlockView... blocks) {
            this.blocks = List.of(blocks);
        }

        @Override
        public List<SttBlockView> findByMeeting(long meetingId) {
            queried = true;
            return blocks;
        }

        @Override
        public Optional<SttBlockView> findOne(long meetingId, int blockSeq) {
            queried = true;
            return blocks.stream().filter(block -> block.blockSeq() == blockSeq).findFirst();
        }

        /* 분석 시작 관문이 쓰는 값이다 — 이 서비스(STT-03·04)는 부르지 않는다. */
        @Override
        public int countUnfinished(long meetingId) {
            throw new UnsupportedOperationException("STT-03·04 는 미완 블록 수를 읽지 않는다");
        }

        /* MEET-04 요약 상태 배치 조회가 쓰는 값이다 — 같은 이유로 부르지 않는다. */
        @Override
        public java.util.Set<Long> findMeetingsWithUnfinishedBlocks(List<Long> meetingIds) {
            throw new UnsupportedOperationException("STT-03·04 는 배치 미완 조회를 쓰지 않는다");
        }

        // ── 폴링 워커의 계약. STT-03·04 는 제출까지고 결과 반영은 워커가 한다 ────────────
        @Override
        public List<SttBlockRepository.PendingBlock> findUnfinished(int limit) {
            throw new UnsupportedOperationException("폴링 대상 조회는 워커의 몫이다");
        }

        @Override
        public boolean markRunning(long blockId) {
            throw new UnsupportedOperationException("RUNNING 전이는 제공자 상태를 본 워커가 한다");
        }

        @Override
        public boolean markDone(long blockId) {
            throw new UnsupportedOperationException("DONE 전이는 정본 적재 뒤에 워커가 한다");
        }

        @Override
        public boolean markFailed(long blockId, String errorCode) {
            throw new UnsupportedOperationException("FAILED 전이는 제공자 상태를 본 워커가 한다");
        }

        @Override
        public boolean recoverAudioSpan(long blockId, int endOffsetMs) {
            throw new UnsupportedOperationException("duration 복구는 폴링 워커의 몫이다");
        }

        @Override
        public boolean markQueuedForRetry(long blockId, int expectedRetryCount, String provider,
                                          String providerJobName) {
            this.expectedRetryCount = expectedRetryCount;
            if (loseRace) {
                return false;
            }
            savedJobName = providerJobName;
            return true;
        }

        @Override
        public long createQueued(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs,
                                 String cutReason, String audioS3Key, String provider, String providerJobName) {
            throw new UnsupportedOperationException("이 테스트는 STT-03/04만 다룬다 — 자동 생성은 대상 밖.");
        }
    }

    private static final class RecordingJobPort implements SttJobPort {

        private final List<SttJob> submitted = new ArrayList<>();

        @Override
        public void submit(SttJob job) {
            submitted.add(job);
        }
    }
}
