package com.module06.backend.capture.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository.SttBlockView;
import com.module06.backend.capture.application.port.out.SttJobPort;
import com.module06.backend.capture.application.port.out.SttJobPort.SttJob;
import com.module06.backend.capture.application.usecase.GetSttBlocksUseCase;
import com.module06.backend.capture.application.usecase.RetrySttBlockUseCase;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * STT-03(블록 상태 조회) · STT-04(블록 재처리).
 *
 * <h2>회사 스코프는 관문이 유일한 방어선이다</h2>
 * stt_block 에는 company_id 가 없다. 그래서 조회 조건으로는 회사를 막을 수 없고,
 * {@link MeetingAccessGuard} 를 먼저 지나는 것 말고 다른 방법이 없다 — CAP-06 이 그 검증을
 * 빠뜨려 뚫려 있던 자리와 같은 성질이다(#100).
 *
 * <h2>재처리는 실패한 블록만이다</h2>
 * 성공했거나 아직 도는 블록을 다시 돌리면 같은 구간에 STT 요금이 두 번 나가고, 이미 들어온
 * 발화 위에 같은 결과가 덮인다. 화면이 버튼을 가려도 id 를 직접 넣는 경로가 남으므로 여기서
 * 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttBlockService implements GetSttBlocksUseCase, RetrySttBlockUseCase {

    private final SttBlockRepository sttBlockRepository;
    private final SttJobPort sttJobPort;
    private final MeetingAccessGuard meetingAccessGuard;
    private final SttJobNameFactory sttJobNameFactory;

    @Override
    @Transactional(readOnly = true)
    public List<SttBlockView> getSttBlocks(long companyId, long meetingId) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);
        return sttBlockRepository.findByMeeting(meetingId);
    }

    /*
     * <h2>제출과 상태 기록을 한 트랜잭션에 둔다</h2>
     * 벡터 색인 워커(TupleVectorSyncService)와 반대 판단이다. 그쪽은 배경 작업이라 외부 호출을
     * 트랜잭션 밖에 뒀지만, 여기는 **사람이 버튼을 누르고 202 를 기다리는 자리**다.
     *
     * 제출이 실패했는데 QUEUED 로 커밋되면 화면은 "재처리를 시작했습니다"라고 말하는데 아무 일도
     * 일어나지 않고, **그 블록은 다시 누를 수도 없다** — QUEUED 는 재처리 대상이 아니기 때문이다.
     * 사람 손으로 풀 방법이 없는 상태를 만드는 것보다 롤백하고 오류를 보여주는 편이 낫다.
     */
    @Override
    @Transactional
    public RetryAccepted retry(RetrySttBlockCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());

        SttBlockView block = sttBlockRepository.findOne(command.meetingId(), command.blockSeq())
                // 그 회의의 블록이 아니거나 없는 블록이다. 회의 관문은 blockSeq 를 보지 않는다.
                .orElseThrow(() -> new BusinessException(CaptureErrorCode.STT_BLOCK_NOT_FOUND));

        if (block.status() != SttBlockStatus.FAILED) {
            throw new BusinessException(CaptureErrorCode.STT_BLOCK_NOT_RETRYABLE);
        }
        if (block.audioS3Key() == null || block.audioS3Key().isBlank()) {
            /*
             * 다시 돌릴 오디오가 없다. 여기서 막지 않으면 제공자에 빈 참조를 제출하고, 그
             * 실패가 **재시도 횟수만 올린 채** 같은 자리로 돌아온다 — 사람이 버튼을 눌러도
             * 아무것도 나아지지 않는 상태가 반복된다.
             */
            throw new BusinessException(CaptureErrorCode.STT_BLOCK_AUDIO_MISSING);
        }

        String provider = command.provider() != null && !command.provider().isBlank()
                ? command.provider()
                // 안 주면 쓰던 제공자를 그대로 쓴다. 기본값을 여기서 지어내면 whisper 로 돌던
                // 블록이 재처리 한 번에 조용히 aws-transcribe 로 바뀐다.
                : block.provider();

        int retryCount = block.retryCount() + 1;
        // 이름은 **계정 내 유일해야 한다** — 같은 이름을 다시 쓰면 제출이 거절되므로 재시도 횟수를
        // 이름에 넣는다(V5.4 주석). UNIQUE 제약이 그 실수를 DB 에서 한 번 더 잡는다. 형식·네임스페이스는
        // SttJobNameFactory 가 갖는다 — 최초 제출(SttBlockCreationService)과 같은 자리를 써야
        // 두 경로가 갈리지 않는다.
        String jobName = sttJobNameFactory.create(command.meetingId(), command.blockSeq(), retryCount);

        /*
         * 상태를 먼저 올린다. **잡 이름에 이 횟수가 들어가기 때문**이다 — 제출 뒤에 올리면
         * 같은 이름으로 두 번 제출하는 창이 생기고, AWS 는 계정 내 중복 이름을 거절한다.
         *
         * 그런데 순서만으로는 부족했다. 조회와 갱신 사이에 다른 요청이 끼어들면 **둘이 같은
         * 스냅샷을 읽어 같은 이름을 만든다** — 그래서 저장소가 쓰기 잠금을 걸고 읽은 값이
         * 그대로인지 다시 확인한다(compare-and-set · CodeRabbit PR #223 지적).
         */
        boolean transitioned = sttBlockRepository.markQueuedForRetry(
                block.id(), block.retryCount(), provider, jobName);
        if (!transitioned) {
            /*
             * 다른 요청이 먼저 가져갔다. **제출하지 않는다** — 그쪽이 이미 같은 블록을 제출했고,
             * 여기서 또 보내면 같은 이름으로 두 번 제출하는 것이 된다.
             *
             * 409 로 답하는 것이 맞다. 사용자 관점에서는 "지금은 재처리할 수 없는 상태"이고,
             * 실제로도 그 블록은 이 순간 FAILED 가 아니다.
             */
            log.info("STT 블록 재처리 경합 — 다른 요청이 먼저 가져갔다. meetingId={} blockSeq={}",
                    command.meetingId(), block.blockSeq());
            throw new BusinessException(CaptureErrorCode.STT_BLOCK_NOT_RETRYABLE);
        }

        sttJobPort.submit(new SttJob(
                command.meetingId(), block.blockSeq(), provider, jobName,
                block.audioS3Key(), block.startOffsetMs(), block.endOffsetMs()));

        log.info("STT 블록 재처리 접수 — meetingId={} blockSeq={} provider={} 시도={} job={}",
                command.meetingId(), block.blockSeq(), provider, retryCount, jobName);

        return new RetryAccepted(block.blockSeq(), SttBlockStatus.QUEUED, retryCount);
    }
}
