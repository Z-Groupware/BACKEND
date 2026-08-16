package com.module06.backend.cap.domain.model;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * 회의당 1행 — CAP 자체 세그먼트/현재 녹음자 북키핑. D(회의) 도메인의 capture_session(지금은
 * 구현돼 있다 — CAP-01)과는 별개 개념이다: "현재 녹음자가 누구인가"는 세그먼트/청크 순번처럼
 * CAP 고유의 청크 스트리밍 북키핑이라 CAP이 직접 소유한다(capture_session은 세션 생명주기만
 * ACTIVE/PAUSED/ENDED로 관리하고, 누가 녹음자인지는 모른다).
 */
public class CaptureUploadState {

    // 세그먼트당 청크 순번 상한 — 업무 제한이 아니라 DoS 방어용(정상 회의는 도달 불가:
    // 15초 청크 × 100,000 ≈ 17일). 미검증 seq가 lastSeq에 그대로 들어가면 상태 조회의
    // 1..lastSeq 순회가 응답·메모리를 폭증시키고, Integer.MAX_VALUE에선 int 오버플로로 무한 루프가 된다.
    public static final int MAX_SEQ = 100_000;

    private final Long meetingId;
    private int segmentSeq;
    private Long recorderPersonId;
    private int lastSeq;
    private int blocksFormed;
    // 마지막으로 형성된 STT 블록의 끝 지점(ms) — 다음 블록이 빈틈·겹침 없이 여기서부터 시작한다.
    private long lastBlockEndOffsetMs;
    // 블록 예약(자리 선점) 기준 진행 오프셋(ms) — lastBlockEndOffsetMs와 다른 값이다.
    // lastBlockEndOffsetMs는 무거운 파이프라인(ffmpeg·AI-01)이 다 끝나야만 정해지는 "실제 절단
    // 지점"이라, 그 사이(몇 초)엔 갱신되지 않는다. 그 창 안에 뒤이은 트리거가 같은 구간을 "아직
    // 문턱 안 넘음"으로 오판해 또 예약해버리는 레이스가 실제로 있었다(k6 정합성 테스트로 재현,
    // block_seq 중복 생성 + STT 이중 제출). reservedUpToOffsetMs는 예약(blocksFormed CAS)과 같은
    // 트랜잭션 안에서 즉시 전진해서, 그 창을 없앤다.
    private long reservedUpToOffsetMs;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private CaptureUploadState(Long meetingId, int segmentSeq, Long recorderPersonId, int lastSeq,
                               int blocksFormed, long lastBlockEndOffsetMs, long reservedUpToOffsetMs,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (meetingId == null) {
            throw new BusinessException(CapErrorCode.CAP_REQUIRED_ID);
        }
        this.meetingId = meetingId;
        this.segmentSeq = segmentSeq;
        this.recorderPersonId = recorderPersonId;
        this.lastSeq = lastSeq;
        this.blocksFormed = blocksFormed;
        this.lastBlockEndOffsetMs = lastBlockEndOffsetMs;
        this.reservedUpToOffsetMs = reservedUpToOffsetMs;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 신규 생성 — 이 회의에 처음 presign을 호출한 사람을 녹음자로 삼아 세그먼트 0부터 시작
    public static CaptureUploadState startWithRecorder(Long meetingId, Long recorderPersonId) {
        return new CaptureUploadState(meetingId, 0, recorderPersonId, 0, 0, 0L, 0L, null, null);
    }

    // DB에서 읽어온 값으로 복원 (JPA 엔티티 → 도메인 모델)
    public static CaptureUploadState restore(Long meetingId, int segmentSeq, Long recorderPersonId, int lastSeq,
                                             int blocksFormed, long lastBlockEndOffsetMs, long reservedUpToOffsetMs,
                                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CaptureUploadState(meetingId, segmentSeq, recorderPersonId, lastSeq, blocksFormed,
                lastBlockEndOffsetMs, reservedUpToOffsetMs, createdAt, updatedAt);
    }

    /**
     * 녹음자 배정/검증. 아직 아무도 녹음 중이 아니면 호출자가 녹음자가 되고, 이미 녹음자가 있으면
     * 본인 확인 — 다른 사람이면 canTakeover(하트비트 30초 초과, CaptureHeartbeatPort 판정)일 때만
     * 교체를 허용한다.
     */
    public void assignOrVerifyRecorder(Long callerId, boolean canTakeover) {
        if (recorderPersonId == null) {
            recorderPersonId = callerId;
            return;
        }
        if (recorderPersonId.equals(callerId)) {
            return;
        }
        if (!canTakeover) {
            throw new BusinessException(CapErrorCode.CAP_NOT_CURRENT_RECORDER);
        }
        // 이어받기 — 녹음자 교체 + 새 MediaRecorder 세션이므로 세그먼트 번호를 올린다.
        //
        // ⚠️ lastSeq·lastBlockEndOffsetMs도 여기서 같이 0으로 리셋한다(CodeRabbit 지적) — 새
        // 세그먼트는 MediaRecorder가 새로 시작하는 독립된 스트림이라 청크 순번도 1부터 다시
        // 온다. 예전엔 이 리셋이 없어서, 이미 있던 RecordingAssemblyService.hasSeqGap(매
        // 세그먼트가 seq=1부터 시작한다고 가정)과 실제 상태가 서로 어긋나 있었다 — 그 모순이
        // 이번 자동 트리거를 만들면서 겉으로 드러났을 뿐, 원래 있던 결함이다.
        recorderPersonId = callerId;
        segmentSeq++;
        lastSeq = 0;
        lastBlockEndOffsetMs = 0L;
        // reservedUpToOffsetMs도 세그먼트 안에서만 의미가 있다 — lastBlockEndOffsetMs와 같은 이유로
        // 같이 리셋한다(안 하면 새 세그먼트의 예약 판정이 이전 세그먼트 값을 물려받아 오염된다).
        reservedUpToOffsetMs = 0L;
    }

    /*
     * assignOrVerifyRecorder를 호출하면 세그먼트가 실제로 올라갈지(이어받기가 성립할지)를
     * 미리 판정한다. 그 판정 로직과 완전히 같은 조건이어야 한다 — 세그먼트가 바뀌기 직전에
     * 이전 세그먼트의 자투리를 TAIL 블록으로 마무리하려면(SttBlockCutTrigger), 실제로 바뀌기
     * 전에 "지금 바뀌려는 참인가"를 물어볼 곳이 필요하다.
     */
    public boolean willChangeSegment(Long callerId, boolean canTakeover) {
        return recorderPersonId != null && !recorderPersonId.equals(callerId) && canTakeover;
    }

    /** 현재 녹음자인지 검증만 한다(상태 변경 없음). 아니면 CAP_NOT_CURRENT_RECORDER — 상태 조회(#4)처럼 읽기 권한 확인용. */
    public void verifyRecorder(Long callerId) {
        if (recorderPersonId == null || !recorderPersonId.equals(callerId)) {
            throw new BusinessException(CapErrorCode.CAP_NOT_CURRENT_RECORDER);
        }
    }

    /** 청크 업로드 완료 통보 반영 — 현재 녹음자만 가능, seq는 1..MAX_SEQ 범위, lastSeq는 단조 증가만 허용. */
    public void recordUpload(Long callerId, int seq) {
        verifyRecorder(callerId);
        // 범위 밖 seq를 거부해 lastSeq 오염을 막는다(상태 조회의 무한 리스트·int 오버플로 방지).
        if (seq < 1 || seq > MAX_SEQ) {
            throw new BusinessException(CapErrorCode.CAP_INVALID_SEQ);
        }
        if (seq > lastSeq) {
            lastSeq = seq;
        }
    }

    /*
     * 다음 블록 순번을 확정한다 — ffmpeg·AI-01 같은 무거운 작업을 시작하기 **전에** 호출해야
     * 한다(CodeRabbit 지적: 무거운 작업 이후에 올리면, 그 사이 들어온 다른 트리거가 같은 순번을
     * 계산해서 블록이 두 번 만들어지고 Transcribe에도 두 번 제출될 수 있다).
     *
     * 이 메서드 자체는 동시성을 막지 못한다 — 실제 CAS(compare-and-set)는
     * CaptureUploadStateRepository.tryReserveNextBlockSeq가 DB 쓰기 잠금으로 보장한다(그 저장소가
     * "지금 DB의 blocksFormed가 호출자가 기대한 값과 같을 때만" 이 메서드가 적용된 상태를
     * 저장하도록 조율한다). 그래서 파이프라인이 실패해도 blocksFormed는 이미 전진해 있다 —
     * block_seq에 빈 번호 하나가 남을 뿐(해롭지 않다), 다음 시도가 다시 경합하는 것보다 안전하다.
     *
     * blocksFormed만 올리는 버전(세그먼트 무관 — 회의 전체를 관통하는 번호라서 안전)과, 거기에
     * reservedUpToOffsetMs까지 같이 전진시키는 버전 둘로 나뉜다. reservedUpToOffsetMs는
     * lastBlockEndOffsetMs와 마찬가지로 **그 세그먼트 안에서만** 의미가 있다 — 호출자
     * (tryReserveNextBlockSeq)가 예약 당시의 세그먼트가 지금 세그먼트와 같을 때만 오프셋 버전을
     * 불러야 한다. 다르면(그 사이 이어받기가 일어났으면) 오프셋 없는 버전을 써야, 이미 새
     * 세그먼트용으로 0 리셋된 reservedUpToOffsetMs를 옛 세그먼트의 값으로 오염시키지 않는다.
     *
     * @return 이번에 확정된 블록 순번(증가 전 값)
     */
    public int reserveNextBlockSeq() {
        int reservedSeq = blocksFormed;
        blocksFormed++;
        return reservedSeq;
    }

    /*
     * targetOffsetMs를 reservedUpToOffsetMs에도 같이 반영한다 — blocksFormed CAS와 같은 트랜잭션
     * 안에서 이 구간을 즉시 "선점됨"으로 표시해야, 무거운 파이프라인이 도는 동안 뒤이은 트리거가
     * 같은 구간을 다시 문턱 통과로 오판하지 않는다(k6 정합성 테스트로 재현된 레이스, block_seq
     * 중복 생성 + STT 이중 제출 원인). 호출자(tryReserveNextBlockSeq)가 이미 지금 세그먼트가
     * 예약 당시 세그먼트와 같고 targetOffsetMs > reservedUpToOffsetMs임을 확인한 뒤에만 부르므로,
     * 여기서는 다시 검증하지 않는다.
     */
    public int reserveNextBlockSeqAndAdvanceOffset(long targetOffsetMs) {
        int reservedSeq = reserveNextBlockSeq();
        reservedUpToOffsetMs = targetOffsetMs;
        return reservedSeq;
    }

    /*
     * 예약된 블록이 실제로 완성됐을 때(조립·생성·제출까지 성공) 끝 지점을 갱신한다.
     *
     * <h2>expectedSegmentSeq가 지금 세그먼트와 같을 때만 적용한다(CodeRabbit 지적)</h2>
     * blockSeq(reserveNextBlockSeq)는 회의 전체를 관통하는 번호라 세그먼트 무관하게 유효하지만,
     * lastBlockEndOffsetMs는 **그 세그먼트 안에서만** 의미가 있다. 이 파이프라인이 도는 몇 초
     * 사이(ffmpeg·AI-01 호출) 녹음자 이어받기로 세그먼트가 바뀌면 assignOrVerifyRecorder가
     * lastBlockEndOffsetMs를 이미 0으로 리셋해뒀는데, 그 뒤에 이전 세그먼트의 cutOffsetMs가
     * 도착해서 그대로 적용되면 **새 세그먼트가 이미 그만큼 오디오를 소비한 것처럼** 오염된다.
     * 세그먼트가 다르면 적용을 건너뛴다 — 어차피 그 블록은 이미 만들어져 STT에 제출까지
     * 끝났으니, 카운터에 흔적을 남기지 않는 것 말고는 할 일이 없다.
     *
     * blockEndOffsetMs는 지금까지 기록된 끝 지점보다 커야 한다 — 블록은 항상 앞으로만
     * 진행해야 하고(시간을 거스르는 블록은 없음), 작거나 같은 값이 들어오면 호출자가 순서를
     * 잘못 불렀거나 같은 블록을 중복 처리하려는 것이다.
     *
     * @return 실제로 적용됐으면 true, 세그먼트가 이미 바뀌어 건너뛰었으면 false
     */
    public boolean finalizeBlockOffsetIfSegmentMatches(int expectedSegmentSeq, long blockEndOffsetMs) {
        if (segmentSeq != expectedSegmentSeq) {
            return false;
        }
        if (blockEndOffsetMs <= lastBlockEndOffsetMs) {
            throw new BusinessException(CapErrorCode.CAP_BLOCK_OFFSET_INVALID);
        }
        lastBlockEndOffsetMs = blockEndOffsetMs;
        return true;
    }

    public Long getMeetingId() { return meetingId; }
    public int getSegmentSeq() { return segmentSeq; }
    public Long getRecorderPersonId() { return recorderPersonId; }
    public int getLastSeq() { return lastSeq; }
    public int getBlocksFormed() { return blocksFormed; }
    public long getLastBlockEndOffsetMs() { return lastBlockEndOffsetMs; }
    public long getReservedUpToOffsetMs() { return reservedUpToOffsetMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
