package com.module06.backend.capture.application.port.in;

/*
 * cap(녹음·업로드)이 발견한 녹음 구멍을 capture 에게 기록해달라고 요청하는 인바운드 포트다.
 *
 * <h2>왜 이 포트가 필요한가 — PR #302 의 리뷰 지적에 대한 답이다</h2>
 * 자동 블록 트리거(10분/40청크)는 **있는 청크만 이어붙인다.** CAP-05 의 수동 조립은 seq 연속성을
 * 먼저 검증해 구멍이 있으면 409 로 막지만(RecordingAssemblyService#hasSeqGap), 자동 경로에는
 * 그 검증이 없다 — 업로드 지연 중에 트리거가 발화하면 시간축이 밀린 오디오가 만들어진다.
 *
 * 그때 **트리거를 막는 것은 답이 아니다.** 회의 중에 도는 경로라 되돌릴 방법이 없고, 막으면
 * 그 회의의 받아쓰기가 통째로 멈춘다. 대신 구멍을 **기록**한다 — 분배 확정(RVW-05)이 그것을
 * 보고 막고, 화면이 배너를 띄운다. 실패를 오류로 만드는 대신 사람이 볼 수 있는 사실로 남기는
 * 것이 이 저장소가 고른 방향이다(V5.5 · SttGapRepository 주석).
 *
 * <h2>왜 cap 이 판정하나</h2>
 * 어느 seq 가 빠졌는지는 recording_part 를 아는 쪽만 안다. capture 는 그 테이블을 보지 않고,
 * 보게 만들면 두 도메인이 같은 표를 각자 해석하게 된다.
 *
 * <h2>reason 을 문자열로 받지 않는다</h2>
 * enum 으로 받아 알 수 없는 값이 들어올 자리를 없앤다 — stt_gap.reason 은 DB ENUM 이라
 * 문자열이 어긋나면 삽입 시점에 터지고, 그건 회의 중에 도는 경로다.
 */
public interface RecordSttGapPort {

    /*
     * 녹음 쪽에서 발견한 구멍을 남긴다.
     *
     * 같은 구간을 여러 번 보고해도 한 줄만 남는다(구현체가 구간과 사유로 갈아 끼운다) —
     * 청크가 뒤늦게 도착해 트리거가 다시 판정하는 경우가 있고, 그때 줄이 쌓이면 분배 관문의
     * 미확인 수가 부풀어 사람이 한 번 확인해도 계속 막힌다.
     *
     * @param startOffsetMs 회의 기준 구멍 시작
     * @param endOffsetMs   회의 기준 구멍 끝
     */
    void recordRecordingGap(long meetingId, int startOffsetMs, int endOffsetMs, RecordingGapReason reason);

    enum RecordingGapReason {
        /* 청크가 유실됐다 — 브라우저가 올리지 못했거나 완료 통보가 오지 않았다. */
        UPLOAD_MISSING,
        /* 녹음 자체가 없던 구간이다(일시정지·이어받기 사이). 조립이 무음으로 채운다. */
        ASSEMBLY_GAP,
        /* 오디오는 있는데 소리가 없다 — 마이크가 꺼져 있었다. */
        NO_AUDIO
    }
}
