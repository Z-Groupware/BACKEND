package com.module06.backend.capture.application.port.out;

import java.util.List;

/*
 * stt_gap(V5.5) 접근 포트다. **분배 확정의 관문**(RVW-05)과 처리 상태 조회(CAP-06)가 쓴다.
 *
 * 구멍은 "받아쓰기가 죽어서 아무도 못 들은 구간"이다. 그 구간에서 나온 할 일은 어디에도 없고,
 * 구멍을 모른 채 분배를 확정하면 그 액션은 영구히 사라진다 — 사람은 회의가 온전히 처리된 줄
 * 안다(V5.5 주석).
 *
 * <h2>채우는 쪽이 생겼다 — 받아쓰기 실패</h2>
 * 폴링이 블록을 FAILED 로 닫을 때 그 블록의 구간을 구멍으로 남긴다. 예전 주석이 "채우는 쪽이
 * 아직 없다"고 적어 둔 자리가 여기다.
 *
 * ⚠ 아직 안 채워지는 쪽이 남아 있다 — **청크 유실·조립 구멍**(UPLOAD_MISSING · ASSEMBLY_GAP).
 * 그건 녹음 조각을 아는 cap 이 판정하는 값이라 {@code RecordSttGapPort} 로 열어 뒀다.
 */
public interface SttGapRepository {

    /*
     * @return 사람이 아직 확인하지 않은(resolved_at IS NULL) 구멍 수
     */
    int countUnresolved(long meetingId);

    /* 이 회의의 구멍 전부(CAP-06 응답). 시작 오프셋 순이다. */
    List<GapView> findByMeeting(long meetingId);

    /*
     * 받아쓰기 실패로 생긴 구멍을 남긴다 — **그 블록의 기존 구멍을 갈아 끼운다.**
     *
     * 같은 블록을 여러 번 재처리하면 실패도 여러 번이다. 덧붙이면 같은 구간이 여러 줄 쌓여
     * 분배 관문의 미확인 수가 부풀고, 사람이 한 번 확인해도 나머지가 남아 계속 막힌다.
     *
     * ⚠ 사람이 이미 확인한(resolved_at 이 찍힌) 구멍도 갈아 끼운다. 재처리가 또 실패했다면
     * 그건 **새 사실**이고, 예전 확인 도장을 그대로 물려주면 아무도 다시 안 본 구멍이
     * 확인된 것으로 남는다.
     */
    void replaceSttFailureGap(long meetingId, int blockSeq, int startOffsetMs, int endOffsetMs);

    /*
     * 그 블록의 받아쓰기 구멍을 지운다 — **재처리가 성공했을 때.**
     *
     * 이게 없으면 한 번 실패한 블록은 나중에 성공해도 구멍이 남아 분배가 영구히 막힌다.
     * 사람은 STT-03 에서 그 블록이 DONE 인 것을 보면서 "왜 확정이 안 되지"를 묻게 된다.
     */
    void clearSttFailureGap(long meetingId, int blockSeq);

    /*
     * 녹음 쪽 구멍을 남긴다(cap 이 판정한 값 · RecordSttGapPort 경유).
     *
     * 구간과 사유로 갈아 끼운다 — 블록 순번이 없는 구멍이라 그것으로는 식별할 수 없다.
     * 청크가 뒤늦게 도착해 다시 판정되는 경우가 있고, 그때 줄이 쌓이면 분배 관문의 미확인
     * 수가 부풀어 사람이 한 번 확인해도 계속 막힌다.
     */
    void replaceRecordingGap(long meetingId, int startOffsetMs, int endOffsetMs, String reason);

    /*
     * 구멍 한 구간(CAP-06 응답 모양 그대로).
     *
     * @param mentionedNames 이 구간 자막에서 뽑은 언급 인물. 구간만 알려주면 담당자가 10분을
     *                       다시 듣는데, 여기까지 좁혀주면 30초만 확인한다(V5.5 주석).
     *                       ⚠ 지금은 항상 비어 있다 — 우리 자막 읽기 포트가 rms·오프셋만
     *                       투영하고 **본문을 주지 않는다**(CaptionRepository). 지어내지 않는다
     * @param keywords       같은 이유로 지금은 항상 비어 있다
     */
    record GapView(
            int startOffsetMs,
            int endOffsetMs,
            String reason,
            List<String> mentionedNames,
            List<String> keywords
    ) {
    }
}
