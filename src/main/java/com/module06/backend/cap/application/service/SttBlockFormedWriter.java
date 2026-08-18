package com.module06.backend.cap.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;

/*
 * SttBlockCutTrigger의 실제 DB 쓰기(블록 형성 카운터 갱신)만 담당하는 별도 협력자.
 * CompletePartUploadWriter와 같은 이유로 분리한다 — SttBlockCutTrigger 자신을 this.xxx()로
 * 불렀다면 @Transactional이 프록시를 못 거쳐 안 걸렸을 것이다.
 */
@Component
class SttBlockFormedWriter {

    private static final Logger log = LoggerFactory.getLogger(SttBlockFormedWriter.class);

    private final CaptureUploadStateRepository captureUploadStateRepository;

    SttBlockFormedWriter(CaptureUploadStateRepository captureUploadStateRepository) {
        this.captureUploadStateRepository = captureUploadStateRepository;
    }

    // 예약된 블록(reserveNextBlockSeq로 blocksFormed는 이미 전진해 있음)이 실제로 완성됐을 때
    // 끝 지점만 갱신한다. TAIL 블록은 이걸 부르지 않는다 — 새 세그먼트의 lastBlockEndOffsetMs는
    // assignOrVerifyRecorder가 세그먼트 전환 시점에 이미 0으로 리셋해뒀다.
    //
    // expectedSegmentSeq(예약 당시의 세그먼트)와 지금 세그먼트가 다르면 건너뛴다(CodeRabbit
    // 지적) — 그 사이 이어받기가 일어났다면 이 오프셋은 이미 지나간 세그먼트 것이라, 지금
    // 세그먼트의 lastBlockEndOffsetMs(0으로 갓 리셋됨)에 적용하면 상태가 오염된다.
    @Transactional
    void finalizeBlockOffset(Long meetingId, int expectedSegmentSeq, long cutOffsetMs) {
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElseThrow();
        boolean offsetApplied = state.finalizeBlockOffsetIfSegmentMatches(expectedSegmentSeq, cutOffsetMs);
        if (!offsetApplied) {
            log.info("블록 완료 도착 전에 세그먼트가 이미 바뀜 — 끝 지점 갱신을 건너뜀. "
                    + "meetingId={} expectedSegmentSeq={} 현재segmentSeq={}",
                    meetingId, expectedSegmentSeq, state.getSegmentSeq());
        }
        // ⚠️ 세그먼트가 달라 끝 지점을 안 건드렸어도 **저장은 해야 한다.**
        // finalizeBlockOffsetIfSegmentMatches는 두 분기 모두에서 finalizedBlocksCount를 전진시키는데
        // (그게 그 메서드 주석이 못 박은 계약이다 — "안 그러면 다음 세그먼트의 첫 예약이 이 옛
        // 세그먼트 블록 때문에 영영 막힌다"), findByMeetingId가 돌려주는 것은 엔티티가 아니라
        // toDomain()으로 갓 만든 detached 객체라 더티 체킹이 없다. 예전엔 이 분기에서 save 없이
        // 빠져나가서 그 전진이 통째로 유실됐다 — 즉 계약이 코드에는 있고 DB에는 없었다.
        captureUploadStateRepository.save(state);
    }

    /*
     * 예약은 됐는데 그 블록을 끝내지 못했을 때(조립·절단·제출 중 실패) **예약만 풀어준다.**
     *
     * <h2>왜 필요한가 — "빈 번호 하나가 남을 뿐"이 아니었다</h2>
     * SttBlockCutTrigger는 무거운 작업 전에 blocksFormed를 전진시켜 자리를 찜한다. 그 뒤 실패하면
     * 트리거는 로그만 남기고 넘어가는데(best-effort), 그러면 blocksFormed만 오르고
     * finalizedBlocksCount는 그대로다 — hasNoPendingReservation()이 <b>영구히 false</b>가 되어
     * 그 회의는 남은 시간 동안 블록을 하나도 더 만들지 못한다. 회의가 「요약 중」에서 안 빠져나오는
     * 경로가 이것이다(2026-08-18 P1: STT 제출이 잡 이름 충돌로 실패 → 이 게이트가 닫힌 채로 남음).
     *
     * <h2>lastBlockEndOffsetMs는 건드리지 않는다</h2>
     * 이 블록은 절단 지점을 확정하지 못했다(애초에 실패한 이유가 그것일 수도 있다). 끝 지점을
     * 그대로 두면 다음 블록이 <b>같은 시작점에서</b> 오디오를 조립하므로, 실패한 구간의 소리는
     * 버려지지 않고 다음 블록에 흡수된다(그만큼 긴 블록이 된다). reservedUpToOffsetMs는 예약
     * 시점에 이미 전진했으니 다음 트리거는 40청크를 새로 채워야 발화한다 — 실패한 자리를 즉시
     * 다시 때리며 도는 일이 없다.
     */
    @Transactional
    void releaseFailedReservation(Long meetingId) {
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId).orElseThrow();
        state.markBlockFinalized();
        captureUploadStateRepository.save(state);
        log.warn("실패한 블록 예약을 해제했다 — 이 구간은 다음 블록에 흡수된다. "
                + "meetingId={} blocksFormed={} finalizedBlocksCount={}",
                meetingId, state.getBlocksFormed(), state.getFinalizedBlocksCount());
    }
}
